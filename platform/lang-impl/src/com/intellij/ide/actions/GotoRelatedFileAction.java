/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GotoRelatedFileAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {

    DataContext context = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(context);
    PsiFile psiFile = LangDataKeys.PSI_FILE.getData(context);
    if (psiFile == null) return;

    List<GotoRelatedItem> items = getItems(psiFile, editor, context);
    if (items.isEmpty()) return;
    if (items.size() == 1 && items.get(0).getElement() != null) {
      items.get(0).navigate();
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(items);
    }
    createPopup(items, "Go to Related Files").showInBestPositionFor(context);
  }

  public static JBPopup createPopup(final List<? extends GotoRelatedItem> items, final String title) {
    Object[] elements = new Object[items.size()];
    //todo[nik] move presentation logic to GotoRelatedItem class
    final Map<PsiElement, GotoRelatedItem> itemsMap = new HashMap<PsiElement, GotoRelatedItem>();
    for (int i = 0; i < items.size(); i++) {
      GotoRelatedItem item = items.get(i);
      elements[i] = item.getElement() != null ? item.getElement() : item;
      itemsMap.put(item.getElement(), item);
    }

    return getPsiElementPopup(elements, itemsMap, title, new Processor<Object>() {
      @Override
      public boolean process(Object element) {
        if (element instanceof PsiElement) {
          //noinspection SuspiciousMethodCalls
          itemsMap.get(element).navigate();
        }
        else {
          ((GotoRelatedItem)element).navigate();
        }
        return true;
      }
    }
    );
  }

  private static JBPopup getPsiElementPopup(final Object[] elements, final Map<PsiElement, GotoRelatedItem> itemsMap,
                                           final String title, final Processor<Object> processor) {

    final Ref<Boolean> hasMnemonic = Ref.create(false);
    final DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer() {
      {
        setFocusBorderEnabled(false);
      }

      @Override
      public String getElementText(PsiElement element) {
        String customName = itemsMap.get(element).getCustomName();
        return (customName != null ? customName : super.getElementText(element));
      }

      @Override
      protected Icon getIcon(PsiElement element) {
        Icon customIcon = itemsMap.get(element).getCustomIcon();
        return customIcon != null ? customIcon : super.getIcon(element);
      }

      @Override
      public String getContainerText(PsiElement element, String name) {
        PsiFile file = element.getContainingFile();
        return file != null && !getElementText(element).equals(file.getName())
               ? "(" + file.getName() + ")"
               : null;
      }

      @Override
      protected DefaultListCellRenderer getRightCellRenderer(Object value) {
        return null;
      }

      @Override
      protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                           JList list,
                                                           Object value,
                                                           int index,
                                                           boolean selected,
                                                           boolean hasFocus) {
        final GotoRelatedItem item = (GotoRelatedItem)value;
        Color color = list.getForeground();
        final SimpleTextAttributes nameAttributes = new SimpleTextAttributes(Font.PLAIN, color);
        final String name = item.getCustomName();
        if (name == null) return false;
        renderer.append(name, nameAttributes);
        renderer.setIcon(item.getCustomIcon());
        return true;
      }

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final JPanel component = (JPanel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!hasMnemonic.get()) return component;

        final JPanel panelWithMnemonic = new JPanel(new BorderLayout());
        final int mnemonic = getMnemonic(value, itemsMap);
        final JLabel label = new JLabel("");
        if (mnemonic != -1) {
          label.setText(mnemonic + ".");
          label.setDisplayedMnemonicIndex(0);
        }
        label.setPreferredSize(new JLabel("8.").getPreferredSize());

        final JComponent leftRenderer = (JComponent)component.getComponents()[0];
        component.remove(leftRenderer);
        panelWithMnemonic.setBackground(leftRenderer.getBackground());
        label.setBackground(leftRenderer.getBackground());
        panelWithMnemonic.add(label, BorderLayout.WEST);
        panelWithMnemonic.add(leftRenderer, BorderLayout.CENTER);
        component.add(panelWithMnemonic);
        return component;
      }
    };
    final ListPopupImpl popup = new ListPopupImpl(new BaseListPopupStep<Object>(title, Arrays.asList(elements)) {
      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      public PopupStep onChosen(Object selectedValue, boolean finalChoice) {
        processor.process(selectedValue);
        return super.onChosen(selectedValue, finalChoice);
      }
    }) {
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return renderer;
      }
    };
    popup.setMinimumSize(new Dimension(200, -1));
    for (Object item : elements) {
      final int mnemonic = getMnemonic(item, itemsMap);
      if (mnemonic != -1) {
        final Action action = createNumberAction(mnemonic, popup, itemsMap, processor);
        popup.registerAction(mnemonic + "Action", KeyStroke.getKeyStroke(String.valueOf(mnemonic)), action);
        popup.registerAction(mnemonic + "Action", KeyStroke.getKeyStroke("NUMPAD" + String.valueOf(mnemonic)), action);
        hasMnemonic.set(true);
      }
    }
    return popup;
  }

  @NotNull
  public static List<GotoRelatedItem> getItems(@NotNull PsiFile psiFile, @Nullable Editor editor, @Nullable DataContext dataContext) {
    PsiElement contextElement = psiFile;
    if (editor != null) {
      PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        contextElement = element;
      }
    }

    Set<GotoRelatedItem> items = new LinkedHashSet<GotoRelatedItem>();

    for (GotoRelatedProvider provider : Extensions.getExtensions(GotoRelatedProvider.EP_NAME)) {
      items.addAll(provider.getItems(contextElement));
      if (dataContext != null) {
        items.addAll(provider.getItems(dataContext));
      }
    }
    return new ArrayList<GotoRelatedItem>(items);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(LangDataKeys.PSI_FILE.getData(e.getDataContext()) != null);
  }

  private static Action createNumberAction(final int mnemonic,
                                           final ListPopupImpl listPopup,
                                           final Map<PsiElement, GotoRelatedItem> itemsMap,
                                           final Processor<Object> processor) {
      return new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          for (final Object item : listPopup.getListStep().getValues()) {
            if (getMnemonic(item, itemsMap) == mnemonic) {
              listPopup.setFinalRunnable(new Runnable() {
                @Override
                public void run() {
                  processor.process(item);
                }
              });
              listPopup.closeOk(null);
            }
          }
        }
      };
    }

  private static int getMnemonic(Object item, Map<PsiElement, GotoRelatedItem> itemsMap) {
    return (item instanceof GotoRelatedItem ? (GotoRelatedItem)item : itemsMap.get((PsiElement)item)).getMnemonic();
  }
}
