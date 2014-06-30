/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

class ScratchWidget extends EditorBasedWidget implements CustomStatusBarWidget.Multiframe, CustomStatusBarWidget {
  public static final String ID = "Scratch";
  private final MyTextPanel myPanel = new MyTextPanel();

  public ScratchWidget(Project project) {
    super(project);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        Project project = getProject();
        Editor editor = getEditor();
        VirtualFile selectedFile = getSelectedFile();
        if (project == null || editor == null || selectedFile == null) return false;

        DataContext dataContext = createDataContext(editor, myPanel, selectedFile, project);
        actionPerformed(dataContext);
        update();
        return true;
      }
    }.installOn(myPanel);
  }

  @NotNull
  @Override
  public String ID() {
    return ID;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  private void update() {
    VirtualFile file = getSelectedFile();
    boolean enabled = checkEnabled(file);
    if (enabled) {
      Language lang = ((LightVirtualFile)file).getLanguage();
      myPanel.setText(lang.getDisplayName());
      myPanel.setBorder(WidgetBorder.INSTANCE);
      myPanel.setIcon(getDefaultIcon(lang));
    }
    else {
      myPanel.setBorder(null);
    }
    myPanel.setVisible(enabled);
    if (myStatusBar != null) myStatusBar.updateWidget(ID);
  }

  @NotNull
  public static DataContext createDataContext(Editor editor, Component component, VirtualFile selectedFile, Project project) {
    DataContext parent = DataManager.getInstance().getDataContext(component);
    DataContext context = SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT.getName(), editor == null ? null : editor.getComponent(), parent);
    DataContext projectContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT.getName(), project, context);
    return SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE.getName(), selectedFile, projectContext);
  }

  void actionPerformed(DataContext context) {
    ListPopup popup = createPopup(context);
    if (popup != null) {
      Dimension dimension = popup.getContent().getPreferredSize();
      Point at = new Point(0, -dimension.height);
      popup.show(new RelativePoint(myPanel, at));
    }
  }

  @Nullable
  private ListPopup createPopup(DataContext context) {
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    boolean enabled = checkEnabled(virtualFile);
    if (!enabled) return null;

    List<Language> languages = CreateScratchFileAction.getLanguages();
    DefaultActionGroup group = createActionGroup(virtualFile, languages);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Choose language", group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    return CreateScratchFileAction.updatePopupSize(popup, languages);
  }

  @NotNull
  private DefaultActionGroup createActionGroup(@NotNull VirtualFile virtualFile, @NotNull List<Language> languages) {
    List<AnAction> list = ContainerUtil.newArrayListWithCapacity(languages.size());
    for (Language language : languages) {
      list.add(new ChangeLanguageAction(virtualFile, language));
    }
    return new DefaultActionGroup("Change language", list);
  }

  @Contract("null -> false")
  private static boolean checkEnabled(@Nullable VirtualFile virtualFile) {
    return virtualFile != null && virtualFile.getFileSystem() instanceof ScratchpadFileSystem;
  }

  @Override
  public StatusBarWidget copy() {
    return new ScratchWidget(myProject);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    update();
    super.fileOpened(source, file);
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    update();
    super.fileClosed(source, file);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    update();
    super.selectionChanged(event);
  }

  private static Icon getDefaultIcon(@NotNull Language language) {
    LanguageFileType associatedLanguage = language.getAssociatedFileType();
    return associatedLanguage != null ? associatedLanguage.getIcon() : null;
  }

  private static class MyTextPanel extends TextPanel {
    private int myIconTextGap = 2;
    private Icon myIcon;

    @Override
    protected void paintComponent(@NotNull final Graphics g) {
      super.paintComponent(g);
      if (getText() != null) {
        Rectangle r = getBounds();
        Insets insets = getInsets();
        AllIcons.Ide.Statusbar_arrows.paintIcon(this, g, r.width - insets.right - AllIcons.Ide.Statusbar_arrows.getIconWidth() - 2,
                                                r.height / 2 - AllIcons.Ide.Statusbar_arrows.getIconHeight() / 2);
        if (myIcon != null) {
          myIcon.paintIcon(this, g, insets.left - myIconTextGap - myIcon.getIconWidth(), r.height / 2 - myIcon.getIconHeight() / 2);
        }
      }
    }

    @NotNull
    @Override
    public Insets getInsets() {
      Insets insets = super.getInsets();
      if (myIcon != null) {
        insets.left += myIcon.getIconWidth() + myIconTextGap * 2;
      }
      return insets;
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      int deltaWidth = AllIcons.Ide.Statusbar_arrows.getIconWidth() + myIconTextGap * 2;
      if (myIcon != null) {
        deltaWidth += myIcon.getIconWidth() + myIconTextGap * 2;
      }
      return new Dimension(preferredSize.width + deltaWidth, preferredSize.height);
    }

    public void setIcon(Icon icon) {
      myIcon = icon;
    }
  }

  private class ChangeLanguageAction extends AnAction implements DumbAware {
    private final VirtualFile myVirtualFile;

    private final Language myLanguage;

    public ChangeLanguageAction(@NotNull VirtualFile virtualFile, @NotNull Language language) {
      super(language.getDisplayName(), "", getDefaultIcon(language));
      myVirtualFile = virtualFile;
      myLanguage = language;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      if (myVirtualFile instanceof LightVirtualFile) {
        ((LightVirtualFile)myVirtualFile).setLanguage(myLanguage);
        FileContentUtil.reparseFiles(project, Collections.singletonList(myVirtualFile), false);
        ScratchWidget.this.update();
      }
    }
  }
}
