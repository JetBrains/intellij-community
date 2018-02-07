// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ComponentWithBrowseButton<Comp extends JComponent> extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(ComponentWithBrowseButton.class);

  private final Comp myComponent;
  private final FixedSizeButton myBrowseButton;
  private boolean myButtonEnabled = true;

  public ComponentWithBrowseButton(Comp component, @Nullable ActionListener browseActionListener) {
    super(new BorderLayout(SystemInfo.isMac || UIUtil.isUnderDarcula() ? 0 : 2, 0));

    myComponent = component;
    // required! otherwise JPanel will occasionally gain focus instead of the component
    setFocusable(false);
    boolean inlineBrowseButton = myComponent instanceof ExtendableTextField && Experiments.isFeatureEnabled("inline.browse.button");
    if (inlineBrowseButton) {
      ExtendableTextField.Extension action = new ExtendableTextField.Extension() {
        @Override
        public Icon getIcon(boolean hovered) {
          return hovered ? AllIcons.General.OpenDiskHover : AllIcons.General.OpenDisk;
        }

        @Override
        public String getTooltip() {
          return UIBundle.message("component.with.browse.button.browse.button.tooltip.text");
        }

        @Override
        public Runnable getActionOnClick() {
          return () -> {
            for (ActionListener listener : myBrowseButton.getActionListeners()) {
              listener.actionPerformed(new ActionEvent(myComponent, ActionEvent.ACTION_PERFORMED, "action"));
            }
          };
        }
      };
      ((ExtendableTextField)myComponent).addExtension(action);
      new DumbAwareAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          action.getActionOnClick().run();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myComponent);
    }
    add(myComponent, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(myComponent);
    if (browseActionListener != null) {
      myBrowseButton.addActionListener(browseActionListener);
    }
    if (!inlineBrowseButton) {
      add(myBrowseButton, BorderLayout.EAST);
    }

    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    // FixedSizeButton isn't focusable but it should be selectable via keyboard.
    if (ApplicationManager.getApplication() != null) {  // avoid crash at design time
      new MyDoClickAction(myBrowseButton).registerShortcut(myComponent);
    }
    if (ScreenReader.isActive()) {
      myBrowseButton.setFocusable(true);
      myBrowseButton.getAccessibleContext().setAccessibleName("Browse");
    }
  }

  public final Comp getChildComponent() {
    return myComponent;
  }

  public void setTextFieldPreferredWidth(final int charCount) {
    JComponent comp = getChildComponent();
    Dimension size = GuiUtils.getSizeByChars(charCount, comp);
    comp.setPreferredSize(size);
    Dimension preferredSize = myBrowseButton.getPreferredSize();

    boolean keepHeight = UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderWin10LookAndFeel();
    preferredSize.setSize(size.width + preferredSize.width + 2,
                          keepHeight ? preferredSize.height : preferredSize.height + 2);

    setPreferredSize(preferredSize);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myBrowseButton.setEnabled(enabled && myButtonEnabled);
    myComponent.setEnabled(enabled);
  }

  public void setButtonEnabled(boolean buttonEnabled) {
    myButtonEnabled = buttonEnabled;
    setEnabled(isEnabled());
  }

  public void setButtonIcon(Icon icon) {
    myBrowseButton.setIcon(icon);
    myBrowseButton.setDisabledIcon(IconLoader.getDisabledIcon(icon));
  }

  /**
   * Adds specified {@code listener} to the browse button.
   */
  public void addActionListener(ActionListener listener){
    myBrowseButton.addActionListener(listener);
  }

  public void removeActionListener(ActionListener listener) {
    myBrowseButton.removeActionListener(listener);
  }

  public void addBrowseFolderListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<Comp> accessor) {
    addActionListener(new BrowseFolderActionListener<>(title, description, this, project, fileChooserDescriptor, accessor));
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(String, String, Project, FileChooserDescriptor, TextComponentAccessor)} instead
   */
  @Deprecated
  public void addBrowseFolderListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<Comp> accessor, boolean autoRemoveOnHide) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
  }

  /**
   * @deprecated use {@link #addActionListener(ActionListener)} instead
   */
  @Deprecated
  @SuppressWarnings("UnusedParameters")
  public void addBrowseFolderListener(@Nullable Project project, final BrowseFolderActionListener<Comp> actionListener) {
    addActionListener(actionListener);
  }

  /**
   * @deprecated use {@link #addActionListener(ActionListener)} instead
   */
  @Deprecated
  @SuppressWarnings("UnusedParameters")
  public void addBrowseFolderListener(@Nullable Project project, final BrowseFolderActionListener<Comp> actionListener, boolean autoRemoveOnHide) {
    addActionListener(actionListener);
  }

  @Override
  public void dispose() {
    ActionListener[] listeners = myBrowseButton.getActionListeners();
    for (ActionListener listener : listeners) {
      myBrowseButton.removeActionListener(listener);
    }
  }

  public FixedSizeButton getButton() {
    return myBrowseButton;
  }

  /**
   * Do not use this class directly it is public just to hack other implementation of controls similar to TextFieldWithBrowseButton.
   */
  public static final class MyDoClickAction extends DumbAwareAction {
    private final FixedSizeButton myBrowseButton;
    public MyDoClickAction(FixedSizeButton browseButton) {
      myBrowseButton = browseButton;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myBrowseButton.isVisible() && myBrowseButton.isEnabled());
    }

    @Override
    public void actionPerformed(AnActionEvent e){
      myBrowseButton.doClick();
    }

    public void registerShortcut(JComponent textField) {
      ShortcutSet shiftEnter = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
      registerCustomShortcutSet(shiftEnter, textField);
      myBrowseButton.setToolTipText(KeymapUtil.getShortcutsText(shiftEnter.getShortcuts()));
    }

    public static void addTo(FixedSizeButton browseButton, JComponent aComponent) {
      new MyDoClickAction(browseButton).registerShortcut(aComponent);
    }
  }

  public static class BrowseFolderActionListener<T extends JComponent> implements ActionListener {
    private final String myTitle;
    private final String myDescription;
    protected ComponentWithBrowseButton<T> myTextComponent;
    private final TextComponentAccessor<T> myAccessor;
    private Project myProject;
    protected final FileChooserDescriptor myFileChooserDescriptor;

    public BrowseFolderActionListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      ComponentWithBrowseButton<T> textField,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<T> accessor) {
      if (fileChooserDescriptor != null && fileChooserDescriptor.isChooseMultiple()) {
        LOG.error("multiple selection not supported");
        fileChooserDescriptor = new FileChooserDescriptor(fileChooserDescriptor) {
          @Override
          public boolean isChooseMultiple() {
            return false;
          }
        };
      }

      myTitle = title;
      myDescription = description;
      myTextComponent = textField;
      myProject = project;
      myFileChooserDescriptor = fileChooserDescriptor;
      myAccessor = accessor;
    }

    @Nullable
    protected Project getProject() {
      return myProject;
    }

    protected void setProject(@Nullable Project project) {
      myProject = project;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      FileChooserDescriptor fileChooserDescriptor = myFileChooserDescriptor;
      if (myTitle != null || myDescription != null) {
        fileChooserDescriptor = (FileChooserDescriptor)myFileChooserDescriptor.clone();
        if (myTitle != null) {
          fileChooserDescriptor.setTitle(myTitle);
        }
        if (myDescription != null) {
          fileChooserDescriptor.setDescription(myDescription);
        }
      }

      FileChooser.chooseFile(fileChooserDescriptor, getProject(), myTextComponent, getInitialFile(), this::onFileChosen);
    }

    @Nullable
    protected VirtualFile getInitialFile() {
      String directoryName = getComponentText();
      if (StringUtil.isEmptyOrSpaces(directoryName)) {
        return null;
      }

      directoryName = FileUtil.toSystemIndependentName(directoryName);
      VirtualFile path = LocalFileSystem.getInstance().findFileByPath(expandPath(directoryName));
      while (path == null && directoryName.length() > 0) {
        int pos = directoryName.lastIndexOf('/');
        if (pos <= 0) break;
        directoryName = directoryName.substring(0, pos);
        path = LocalFileSystem.getInstance().findFileByPath(directoryName);
      }
      return path;
    }

    @NotNull
    protected String expandPath(@NotNull String path) {
      return path;
    }

    protected String getComponentText() {
      return myAccessor.getText(myTextComponent.getChildComponent()).trim();
    }

    @NotNull
    protected String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
      return chosenFile.getPresentableUrl();
    }

    protected void onFileChosen(@NotNull VirtualFile chosenFile) {
      myAccessor.setText(myTextComponent.getChildComponent(), chosenFileToResultingText(chosenFile));
    }
  }

  @Override
  public final void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
      IdeFocusManager.getGlobalInstance().requestFocus(myComponent, true));
  }

  @SuppressWarnings("deprecation")
  @Override
  public final void setNextFocusableComponent(Component aComponent) {
    super.setNextFocusableComponent(aComponent);
    myComponent.setNextFocusableComponent(aComponent);
  }

  private KeyEvent myCurrentEvent = null;

  @Override
  protected final boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (condition == WHEN_FOCUSED && myCurrentEvent != e) {
      try {
        myCurrentEvent = e;
        myComponent.dispatchEvent(e);
      }
      finally {
        myCurrentEvent = null;
      }
    }
    if (e.isConsumed()) return true;
    return super.processKeyBinding(ks, e, condition, pressed);
  }
}
