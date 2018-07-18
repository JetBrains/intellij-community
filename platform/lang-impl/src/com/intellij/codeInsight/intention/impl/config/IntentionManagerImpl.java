/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.CleanupOnScopeIntention;
import com.intellij.codeInsight.daemon.impl.EditCleanupProfileIntentionAction;
import com.intellij.codeInsight.intention.*;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.GlobalSimpleInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.actions.CleanupAllIntention;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dsl
 */
public class IntentionManagerImpl extends IntentionManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl");

  private final List<IntentionAction> myActions = ContainerUtil.createLockFreeCopyOnWriteList();
  private final IntentionManagerSettings mySettings;
  private boolean myIntentionsDisabled;

  private final Alarm myInitActionsAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  public IntentionManagerImpl(IntentionManagerSettings intentionManagerSettings) {
    mySettings = intentionManagerSettings;

    addAction(new EditInspectionToolsSettingsInSuppressedPlaceIntention());

    final ExtensionPoint<IntentionActionBean> point = Extensions.getArea(null).getExtensionPoint(EP_INTENTION_ACTIONS);

    point.addExtensionPointListener(new ExtensionPointListener<IntentionActionBean>() {
      @Override
      public void extensionAdded(@NotNull final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
        registerIntentionFromBean(extension);
      }

      @Override
      public void extensionRemoved(@NotNull final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
      }
    });
  }

  private void registerIntentionFromBean(@NotNull final IntentionActionBean extension) {
    final Runnable runnable = () -> {
      final String[] categories = extension.getCategories();
      final IntentionAction instance = createIntentionActionWrapper(extension, categories);
      if (categories == null) {
        addAction(instance);
      }
      else {
        String descriptionDirectoryName = extension.getDescriptionDirectoryName();
        if (descriptionDirectoryName == null) {
          registerIntentionAndMetaData(instance, categories);
        }
        else {
          addAction(instance);
          mySettings.registerIntentionMetaData(instance, categories, descriptionDirectoryName, extension.getMetadataClassLoader());
        }
      }
    };
    //todo temporary hack, need smarter logic:
    // * on the first request, wait until all the initialization is finished
    // * ensure this request doesn't come on EDT
    // * while waiting, check for ProcessCanceledException
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      myInitActionsAlarm.addRequest(runnable, 300);
    }
  }

  @Override
  public void dispose() {

  }

  private static IntentionAction createIntentionActionWrapper(@NotNull IntentionActionBean intentionActionBean, String[] categories) {
    return new IntentionActionWrapper(intentionActionBean, categories);
  }

  @Override
  public void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String... category) {
    registerIntentionAndMetaData(action, category, getDescriptionDirectoryName(action));
  }

  @NotNull
  private static String getDescriptionDirectoryName(final IntentionAction action) {
    if (action instanceof IntentionActionWrapper) {
      final IntentionActionWrapper wrapper = (IntentionActionWrapper)action;
      return getDescriptionDirectoryName(wrapper.getImplementationClassName());
    }
    else {
      return getDescriptionDirectoryName(action.getClass().getName());
    }
  }

  private static String getDescriptionDirectoryName(final String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1).replaceAll("\\$", "");
  }

  @Override
  public void registerIntentionAndMetaData(@NotNull IntentionAction action,
                                           @NotNull String[] categories,
                                           @NotNull @NonNls String descriptionDirectoryName) {
    addAction(action);
    mySettings.registerIntentionMetaData(action, categories, descriptionDirectoryName);
  }

  @Override
  public void registerIntentionAndMetaData(@NotNull final IntentionAction action,
                                           @NotNull final String[] category,
                                           @NotNull final String description,
                                           @NotNull final String exampleFileExtension,
                                           @NotNull final String[] exampleTextBefore,
                                           @NotNull final String[] exampleTextAfter) {
    addAction(action);

    IntentionActionMetaData metaData = new IntentionActionMetaData(action, category,
                                                                   new PlainTextDescriptor(description, "description.html"),
                                                                   mapToDescriptors(exampleTextBefore, "before." + exampleFileExtension),
                                                                   mapToDescriptors(exampleTextAfter, "after." + exampleFileExtension));
    mySettings.registerMetaData(metaData);
  }

  @Override
  public void unregisterIntention(@NotNull IntentionAction intentionAction) {
    myActions.remove(intentionAction);
    mySettings.unregisterMetaData(intentionAction);
  }

  @NotNull
  private static TextDescriptor[] mapToDescriptors(String[] texts, @NonNls String fileName) {
    TextDescriptor[] result = new TextDescriptor[texts.length];
    for (int i = 0; i < texts.length; i++) {
      result[i] = new PlainTextDescriptor(texts[i], fileName);
    }
    return result;
  }

  @Override
  @NotNull
  public List<IntentionAction> getStandardIntentionOptions(@NotNull final HighlightDisplayKey displayKey,
                                                           @NotNull final PsiElement context) {
    checkForDuplicates();
    List<IntentionAction> options = new ArrayList<>(9);
    options.add(new EditInspectionToolsSettingsAction(displayKey));
    options.add(new RunInspectionIntention(displayKey));
    options.add(new DisableInspectionToolAction(displayKey));
    return options;
  }

  @Nullable
  @Override
  public IntentionAction createFixAllIntention(@NotNull InspectionToolWrapper toolWrapper, @NotNull IntentionAction action) {
    checkForDuplicates();
    if (toolWrapper instanceof GlobalInspectionToolWrapper) {
      final LocalInspectionToolWrapper localWrapper = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
      if (localWrapper != null) {
        return createFixAllIntention(localWrapper, action);
      }
    }

    if (toolWrapper instanceof LocalInspectionToolWrapper) {
      FileModifier fix = action;
      if (action instanceof QuickFixWrapper) {
        fix = ((QuickFixWrapper)action).getFix();
      }
      return new CleanupInspectionIntention(toolWrapper, fix, action.getText());
    }
    if (toolWrapper instanceof GlobalInspectionToolWrapper) {
      GlobalInspectionTool wrappedTool = ((GlobalInspectionToolWrapper)toolWrapper).getTool();
      if (wrappedTool instanceof GlobalSimpleInspectionTool && (action instanceof LocalQuickFix || action instanceof QuickFixWrapper)) {
        FileModifier fix = action;
        if (action instanceof QuickFixWrapper) {
          fix = ((QuickFixWrapper)action).getFix();
        }
        return new CleanupInspectionIntention(toolWrapper, fix, action.getText());
      }
    }
    else {
      throw new AssertionError("unknown tool: " + toolWrapper);
    }
    return null;
  }

  @NotNull
  @Override
  public IntentionAction createCleanupAllIntention() {
    return CleanupAllIntention.INSTANCE;
  }

  @NotNull
  @Override
  public List<IntentionAction> getCleanupIntentionOptions() {
    ArrayList<IntentionAction> options = new ArrayList<>();
    options.add(EditCleanupProfileIntentionAction.INSTANCE);
    options.add(CleanupOnScopeIntention.INSTANCE);
    return options;
  }

  @Override
  @NotNull
  public LocalQuickFix convertToFix(@NotNull final IntentionAction action) {
    if (action instanceof LocalQuickFix) {
      return (LocalQuickFix)action;
    }
    return new LocalQuickFix() {
      @Override
      @NotNull
      public String getName() {
        return action.getText();
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return action.getFamilyName();
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
        try {
          action.invoke(project, new LazyEditor(psiFile), psiFile);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }

  @Override
  public void addAction(@NotNull IntentionAction action) {
    myActions.add(action);
  }

  @Override
  @NotNull
  public IntentionAction[] getIntentionActions() {
    if (myIntentionsDisabled) return IntentionAction.EMPTY_ARRAY;
    return myActions.toArray(IntentionAction.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public IntentionAction[] getAvailableIntentionActions() {
    if (myIntentionsDisabled) return IntentionAction.EMPTY_ARRAY;
    checkForDuplicates();
    List<IntentionAction> list = new ArrayList<>(myActions.size());
    for (IntentionAction action : myActions) {
      if (mySettings.isEnabled(action)) {
        list.add(action);
      }
    }
    return list.toArray(IntentionAction.EMPTY_ARRAY);
  }

  private boolean checkedForDuplicates; // benign data race
  // check that the intention of some class registered only once
  public void checkForDuplicates() {
    if (checkedForDuplicates) {
      return;
    }
    checkedForDuplicates = true;
    List<String> duplicates = myActions.stream()
       .collect(Collectors.groupingBy(action -> unwrap(action).getClass()))
       .values().stream()
       .filter(list -> list.size() > 1)
       .map(dup -> dup.size() + " intention duplicates found for " + unwrap(dup.get(0)))
       .collect(Collectors.toList());

    if (!duplicates.isEmpty()) {
      throw new IllegalStateException(duplicates.toString());
    }
  }

  private static IntentionAction unwrap(IntentionAction action) {
    return action instanceof IntentionActionDelegate ? unwrap(((IntentionActionDelegate)action).getDelegate()) : action;
  }

  public boolean hasActiveRequests() {
    return !myInitActionsAlarm.isEmpty();
  }

  @TestOnly
  public <T extends Throwable> void withDisabledIntentions(ThrowableRunnable<T> runnable) throws T {
    boolean oldIntentionsDisabled = myIntentionsDisabled;
    myIntentionsDisabled = true;
    try {
      runnable.run();
    }
    finally {
      myIntentionsDisabled = oldIntentionsDisabled;
    }
  }
}
