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

package com.intellij.application.options.colors;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class FontEditorPreview implements PreviewPanel{
  private static final String PREVIEW_TEXT_KEY = "FontPreviewText";

  private final EditorEx myEditor;

  private final Supplier<? extends EditorColorsScheme> mySchemeSupplier;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public FontEditorPreview(final Supplier<? extends EditorColorsScheme> schemeSupplier, boolean editable) {
    mySchemeSupplier = schemeSupplier;

    @Nls String text = PropertiesComponent.getInstance().getValue(PREVIEW_TEXT_KEY, getIDEDemoText());

    myEditor = (EditorEx)createPreviewEditor(text, mySchemeSupplier.get(), editable);
    registerRestoreAction(myEditor);
    installTrafficLights(myEditor);
  }

  private static void registerRestoreAction(EditorEx editor) {
    editor.putUserData(RestorePreviewTextAction.OUR_EDITOR, Boolean.TRUE);
    AnAction restoreAction = ActionManager.getInstance().getAction(IdeActions.ACTION_RESTORE_FONT_PREVIEW_TEXT);
    if (restoreAction != null) {
      String originalGroupId = editor.getContextMenuGroupId();
      AnAction originalGroup = originalGroupId == null ? null : ActionManager.getInstance().getAction(originalGroupId);
      DefaultActionGroup group = new DefaultActionGroup();
      if (originalGroup instanceof ActionGroup) {
        group.addAll(((ActionGroup)originalGroup).getChildren(null));
      }
      group.add(restoreAction);
      editor.installPopupHandler(new ContextMenuPopupHandler.Simple(group));
    }
  }

  private static String getIDEDemoText() {
    return
      ApplicationNamesInfo.getInstance().getFullProductName() +
      " is a full-featured IDE\n" +
      "with a high level of usability and outstanding\n" +
      "advanced code editing and refactoring support.\n" +
      "\n" +
      "abcdefghijklmnopqrstuvwxyz 0123456789 (){}[]\n" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ +-*/= .,;:!? #&$%@|^\n" +
      // Create empty lines in order to make the gutter wide enough to display two-digits line numbers (other previews use long text
      // and we don't want different gutter widths on color pages switching).
      "\n" +
      "<!-- -- != := === >= >- >=> |-> -> <$> </> #[ |||> |= ~@\n" +
      "\n";
  }

  static void installTrafficLights(@NotNull EditorEx editor) {
    EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
    markupModel.setErrorStripeRenderer(new DumbTrafficLightRenderer());
    markupModel.setErrorStripeVisible(true);
  }

  static Editor createPreviewEditor(String text, EditorColorsScheme scheme, boolean editable) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    // enable editor popup toolbar
    FileDocumentManagerImpl.registerDocument(editorDocument, new LightVirtualFile());
    EditorEx editor = (EditorEx) (editable ? editorFactory.createEditor(editorDocument) : editorFactory.createViewer(editorDocument));
    editor.setColorsScheme(scheme);
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(true);
    settings.setWhitespacesShown(true);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setRightMarginShown(true);
    settings.setRightMargin(60);
    return editor;
  }

  @Override
  public Component getPanel() {
    return myEditor.getComponent();
  }

  @Override
  public void updateView() {
    EditorColorsScheme scheme = updateOptionsScheme(mySchemeSupplier.get());

    myEditor.setColorsScheme(scheme);
    myEditor.reinitSettings();

  }

  protected EditorColorsScheme updateOptionsScheme(EditorColorsScheme selectedScheme) {
    return selectedScheme;
  }

  @Override
  public void blinkSelectedHighlightType(Object description) {
  }

  @Override
  public void addListener(@NotNull final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void disposeUIResources() {
    String previewText = myEditor.getDocument().getText();
    if (previewText.equals(getIDEDemoText())) {
      PropertiesComponent.getInstance().unsetValue(PREVIEW_TEXT_KEY);
    }
    else {
      PropertiesComponent.getInstance().setValue(PREVIEW_TEXT_KEY, previewText);
    }
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  private static class DumbTrafficLightRenderer implements ErrorStripeRenderer {
    @Override
    public void paint(@NotNull Component c, Graphics g, @NotNull Rectangle r) {
      Icon icon = AllIcons.General.InspectionsOK;
      icon.paintIcon(c, g, r.x, r.y);
    }
  }

  public static class RestorePreviewTextAction extends DumbAwareAction {
    private static final Key<Boolean> OUR_EDITOR = Key.create("RestorePreviewTextAction.editor");

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      e.getPresentation().setEnabledAndVisible(editor != null &&
                                               editor.getUserData(OUR_EDITOR) != null &&
                                               !editor.getDocument().getText().equals(getIDEDemoText()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        WriteCommandAction.runWriteCommandAction(editor.getProject(), null, null, () -> {
          editor.getDocument().setText(getIDEDemoText());
        });
      }
    }
  }
}
