// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RenameHandlerRegistry {
  public static final Key<Boolean> SELECT_ALL = Key.create("rename.selectAll");
  private final PsiElementRenameHandler myDefaultElementRenameHandler;
  private Function<? super Collection<? extends RenameHandler>, ? extends RenameHandler> myRenameHandlerSelectorInTests = ContainerUtil::getFirstItem;

  public static RenameHandlerRegistry getInstance() {
    return ApplicationManager.getApplication().getService(RenameHandlerRegistry.class);
  }

  protected RenameHandlerRegistry() {
    // should be checked last
    myDefaultElementRenameHandler = new PsiElementRenameHandler();
  }

  public boolean hasAvailableHandler(@NotNull DataContext dataContext) {
    for (RenameHandler renameHandler : RenameHandler.EP_NAME.getExtensionList()) {
      if (renameHandler.isAvailableOnDataContext(dataContext)) return true;
    }
    return myDefaultElementRenameHandler.isAvailableOnDataContext(dataContext);
  }

  public @Nullable RenameHandler getRenameHandler(@NotNull DataContext dataContext) {
    List<? extends RenameHandler> availableHandlers = getRenameHandlers(dataContext);
    if (availableHandlers.isEmpty()) {
      return null;
    }
    else if (availableHandlers.size() == 1) {
      return availableHandlers.get(0);
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRenameHandlerSelectorInTests.apply(availableHandlers);
    }
    else {
      Map<String, ? extends List<? extends RenameHandler>> title2Handlers = availableHandlers.stream().collect(
        Collectors.groupingBy(it -> getHandlerTitle(it))
      );
      final String[] strings = ArrayUtilRt.toStringArray(title2Handlers.keySet());
      final HandlersChooser chooser = new HandlersChooser(CommonDataKeys.PROJECT.getData(dataContext), strings);
      if (chooser.showAndGet()) {
        return ContainerUtil.getLastItem(title2Handlers.get(chooser.getSelection()));
      }
      throw new ProcessCanceledException();
    }
  }

  /**
   * Must not show dialogs.
   */
  public @NotNull List<? extends @NotNull RenameHandler> getRenameHandlers(@NotNull DataContext dataContext) {
    try (AccessToken ignore = ProhibitAWTEvents.start("getRenameHandlers")) {
      return doGetRenameHandlers(dataContext);
    }
  }

  private @NotNull List<? extends @NotNull RenameHandler> doGetRenameHandlers(@NotNull DataContext dataContext) {
    final Map<String, RenameHandler> availableHandlers = new TreeMap<>();
    for (RenameHandler renameHandler : RenameHandler.EP_NAME.getExtensionList()) {
      if (renameHandler.isRenaming(dataContext)) {
        availableHandlers.put(getHandlerTitle(renameHandler), renameHandler);
      }
    }
    if (availableHandlers.size() == 1) {
      return new SmartList<>(availableHandlers.values());
    }
    for (Iterator<Map.Entry<String, RenameHandler>> iterator = availableHandlers.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, RenameHandler> entry = iterator.next();
      if (entry.getValue() instanceof MemberInplaceRenameHandler) {
        iterator.remove();
        break;
      }
    }
    if (availableHandlers.isEmpty() && myDefaultElementRenameHandler.isRenaming(dataContext)) {
      return Collections.singletonList(myDefaultElementRenameHandler);
    }
    return new SmartList<>(availableHandlers.values());
  }

  @TestOnly
  public void setRenameHandlerSelectorInTests(Function<? super Collection<? extends RenameHandler>, ? extends RenameHandler> selector, Disposable parentDisposable) {
    myRenameHandlerSelectorInTests = selector;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myRenameHandlerSelectorInTests = ContainerUtil::getFirstItem;
      }
    });
  }

  public static @Nls(capitalization = Nls.Capitalization.Sentence) String getHandlerTitle(RenameHandler renameHandler) {
    if (renameHandler instanceof TitledHandler) {
      return StringUtil.capitalize(StringUtil.toLowerCase(((TitledHandler)renameHandler).getActionTitle()));
    }
    @NlsSafe String handlerToString = renameHandler.toString();
    return handlerToString;
  }

  private static class HandlersChooser extends DialogWrapper {
    private final @NlsContexts.RadioButton String[] myRenamers;
    private String mySelection;
    private final JRadioButton[] myRButtons;

    protected HandlersChooser(Project project, @NlsContexts.RadioButton String [] renamers) {
      super(project);
      myRenamers = renamers;
      myRButtons = new JRadioButton[myRenamers.length];
      mySelection = renamers[0];
      setTitle(RefactoringBundle.message("select.refactoring.title"));
      init();
    }

    @Override
    protected JComponent createNorthPanel() {
      final JPanel radioPanel = new JPanel();
      radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
      final JLabel descriptionLabel = new JLabel(RefactoringBundle.message("what.would.you.like.to.do"));
      descriptionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
      radioPanel.add(descriptionLabel);
      final ButtonGroup bg = new ButtonGroup();
      boolean selected = true;
      int rIdx = 0;
      for (final @NlsContexts.RadioButton String renamer : myRenamers) {
        final JRadioButton rb = new JRadioButton(renamer, selected);
        myRButtons[rIdx++] = rb;
        final ItemListener listener = new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (rb.isSelected()) {
              mySelection = renamer;
            }
          }
        };
        rb.addItemListener(listener);
        selected = false;
        bg.add(rb);
        radioPanel.add(rb);
      }
      RadioUpDownListener.installOn(myRButtons);
      return radioPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myRButtons[0];
    }

    public String getSelection() {
      return mySelection;
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }
  }
}
