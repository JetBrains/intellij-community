/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/*
 * @author: MYakovlev
 * Date: Jul 26, 2002
 * Time: 12:46:00 PM
 */

public class FileTemplateConfigurable implements Configurable, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable");
  @NonNls private static final String EMPTY_HTML = "<html></html>";

  private JPanel myMainPanel;
  private FileTemplate myTemplate;
  private PsiFile myFile;
  private Editor myTemplateEditor;
  private JTextField myNameField;
  private JTextField myExtensionField;
  private JCheckBox myAdjustBox;
  private JCheckBox myLiveTemplateBox;
  private JPanel myTopPanel;
  private JEditorPane myDescriptionComponent;
  private boolean myModified = false;
  private URL myDefaultDescriptionUrl;
  private final Project myProject;

  private final List<ChangeListener> myChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private Splitter mySplitter;
  private final FileType myVelocityFileType = FileTypeManager.getInstance().getFileTypeByExtension("ft");
  private float myProportion = 0.6f;

  public FileTemplateConfigurable(Project project) {
    myProject = project;
  }

  public FileTemplate getTemplate() {
    return myTemplate;
  }

  public void setTemplate(FileTemplate template, URL defaultDescription) {
    myDefaultDescriptionUrl = defaultDescription;
    myTemplate = template;
    if (myMainPanel != null) {
      reset();
      myNameField.selectAll();
      myExtensionField.selectAll();
    }
  }

  public void setShowInternalMessage(String message) {
    myTopPanel.removeAll();
    if (message == null) {
      myTopPanel.add(new JLabel(IdeBundle.message("label.name")),
                     new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                            new Insets(0, 0, 0, 2), 0, 0));
      myTopPanel.add(myNameField,
                     new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                            GridBagConstraints.HORIZONTAL, new Insets(3, 2, 3, 2), 0, 0));
      myTopPanel.add(new JLabel(IdeBundle.message("label.extension")),
                     new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                            new Insets(0, 2, 0, 2), 0, 0));
      myTopPanel.add(myExtensionField,
                     new GridBagConstraints(3, 0, 1, 1, .3, 0.0, GridBagConstraints.CENTER,
                                            GridBagConstraints.HORIZONTAL, new Insets(0, 2, 0, 0), 0, 0));
      myExtensionField.setColumns(7);
    }
    myMainPanel.revalidate();
    myTopPanel.repaint();
  }

  public void setShowAdjustCheckBox(boolean show) {
    myAdjustBox.setEnabled(show);
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.edit.file.template");
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myMainPanel = new JPanel(new GridBagLayout());
    myNameField = new JTextField();
    myExtensionField = new JTextField();
    mySplitter = new Splitter(true, myProportion);
    myAdjustBox = new JCheckBox(IdeBundle.message("checkbox.reformat.according.to.style"));
    myLiveTemplateBox = new JCheckBox(IdeBundle.message("checkbox.enable.live.templates"));
    myTemplateEditor = createEditor();

    myDescriptionComponent = new JEditorPane(UIUtil.HTML_MIME, EMPTY_HTML);
    myDescriptionComponent.setEditable(false);
    myDescriptionComponent.addHyperlinkListener(new BrowserHyperlinkListener());

    myTopPanel = new JPanel(new GridBagLayout());

    JPanel descriptionPanel = new JPanel(new GridBagLayout());
    descriptionPanel.add(SeparatorFactory.createSeparator(IdeBundle.message("label.description"), null),
                         new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                  new Insets(0, 0, 2, 0), 0, 0));
    descriptionPanel.add(ScrollPaneFactory.createScrollPane(myDescriptionComponent),
                         new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                  new Insets(2, 0, 0, 0), 0, 0));

    myMainPanel.add(myTopPanel,
                    new GridBagConstraints(0, 0, 4, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                           GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    myMainPanel.add(mySplitter,
                    new GridBagConstraints(0, 2, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(0, 0, 0, 0), 0, 0));

    mySplitter.setSecondComponent(descriptionPanel);
    setShowInternalMessage(null);

    myNameField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(@NotNull FocusEvent e) {
        onNameChanged();
      }
    });
    myExtensionField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(@NotNull FocusEvent e) {
        onNameChanged();
      }
    });
    myMainPanel.setPreferredSize(JBUI.size(400, 300));
    return myMainPanel;
  }

  public void setProportion(float proportion) {
    myProportion = proportion;
  }

  private Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document doc = myFile == null
                   ? editorFactory.createDocument(myTemplate == null ? "" : myTemplate.getText())
                   : PsiDocumentManager.getInstance(myFile.getProject()).getDocument(myFile);
    assert doc != null;
    Editor editor = editorFactory.createEditor(doc, myProject);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(3);
    editorSettings.setAdditionalLinesCount(3);
    editorSettings.setCaretRowShown(false);

    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        onTextChanged();
      }
    });

    ((EditorEx)editor).setHighlighter(createHighlighter());
    
    JPanel topPanel = new JPanel(new BorderLayout());
    JPanel southPanel = new JPanel(new HorizontalLayout(40));
    southPanel.add(myAdjustBox);
    southPanel.add(myLiveTemplateBox);

    topPanel.add(southPanel, BorderLayout.SOUTH);
    topPanel.add(editor.getComponent(), BorderLayout.CENTER);
    mySplitter.setFirstComponent(topPanel);
    return editor;
  }

  private void onTextChanged() {
    myModified = true;
  }

  public String getNameValue() {
    return myNameField.getText();
  }

  private void onNameChanged() {
    ChangeEvent event = new ChangeEvent(this);
    for (ChangeListener changeListener : myChangeListeners) {
      changeListener.stateChanged(event);
    }
  }

  public void addChangeListener(ChangeListener listener) {
    if (!myChangeListeners.contains(listener)) {
      myChangeListeners.add(listener);
    }
  }

  public void removeChangeListener(ChangeListener listener) {
    myChangeListeners.remove(listener);
  }

  @Override
  public boolean isModified() {
    if (myModified) {
      return true;
    }
    String name = (myTemplate == null) ? "" : myTemplate.getName();
    String extension = (myTemplate == null) ? "" : myTemplate.getExtension();
    if (!Comparing.equal(name, myNameField.getText())) {
      return true;
    }
    if (!Comparing.equal(extension, myExtensionField.getText())) {
      return true;
    }
    if (myTemplate != null) {
      if (myTemplate.isReformatCode() != myAdjustBox.isSelected() || myTemplate.isLiveTemplateEnabled() != myLiveTemplateBox.isSelected()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myTemplate != null) {
      myTemplate.setText(myTemplateEditor.getDocument().getText());
      String name = myNameField.getText();
      String extension = myExtensionField.getText();
      String filename = name + "." + extension;
      if (name.length() == 0 || !isValidFilename(filename)) {
        throw new ConfigurationException(IdeBundle.message("error.invalid.template.file.name.or.extension"));
      }
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(filename);
      if (fileType == UnknownFileType.INSTANCE) {
        FileTypeChooser.associateFileType(filename);
      }
      myTemplate.setName(name);
      myTemplate.setExtension(extension);
      myTemplate.setReformatCode(myAdjustBox.isSelected());
      myTemplate.setLiveTemplateEnabled(myLiveTemplateBox.isSelected());
    }
    myModified = false;
  }

  // TODO: needs to be generalized someday for other profiles
  private static boolean isValidFilename(final String filename) {
    if ( filename.contains("/") || filename.contains("\\") || filename.contains(":") ) {
      return false;
    }
    final File tempFile = new File (FileUtil.getTempDirectory() + File.separator + filename);
    return FileUtil.ensureCanCreateFile(tempFile);
  }

  @Override
  public void reset() {
    final String text = (myTemplate == null) ? "" : myTemplate.getText();
    String name = (myTemplate == null) ? "" : myTemplate.getName();
    String extension = (myTemplate == null) ? "" : myTemplate.getExtension();
    String description = (myTemplate == null) ? "" : myTemplate.getDescription();

    if ((description.length() == 0) && (myDefaultDescriptionUrl != null)) {
      try {
        description = UrlUtil.loadText(myDefaultDescriptionUrl);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    EditorFactory.getInstance().releaseEditor(myTemplateEditor);
    myFile = createFile(text, name);
    myTemplateEditor = createEditor();

    myNameField.setText(name);
    myExtensionField.setText(extension);
    myAdjustBox.setSelected(myTemplate != null && myTemplate.isReformatCode());
    myLiveTemplateBox.setSelected(myTemplate != null && myTemplate.isLiveTemplateEnabled());

    int i = description.indexOf("<html>");
    if (i > 0) {
      description = description.substring(i);
    }
    description = XmlStringUtil.stripHtml(description);
    description = description.replace("\n", "").replace("\r", "");
    description = XmlStringUtil.stripHtml(description);
    description = description + "<hr> <font face=\"verdana\" size=\"-1\"><a href='http://velocity.apache.org/engine/devel/user-guide.html#Velocity_Template_Language_VTL:_An_Introduction'>\n" +
                  "Apache Velocity</a> template language is used</font>";

    myDescriptionComponent.setText(description);
    myDescriptionComponent.setCaretPosition(0);

    myNameField.setEditable((myTemplate != null) && (!myTemplate.isDefault()));
    myExtensionField.setEditable((myTemplate != null) && (!myTemplate.isDefault()));
    myModified = false;
  }

  @Nullable
  private PsiFile createFile(final String text, final String name) {
    if (myTemplate == null) return null;

    final FileType fileType = myVelocityFileType;
    if (fileType == FileTypes.UNKNOWN) return null;

    final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(name + ".txt.ft", fileType, text, 0, true);
    file.getViewProvider().putUserData(FileTemplateManager.DEFAULT_TEMPLATE_PROPERTIES, FileTemplateManager.getInstance(myProject).getDefaultProperties());
    return file;
  }

  @Override
  public void disposeUIResources() {
    myMainPanel = null;
    if (myTemplateEditor != null) {
      EditorFactory.getInstance().releaseEditor(myTemplateEditor);
      myTemplateEditor = null;
    }
    myFile = null;
  }

  private EditorHighlighter createHighlighter() {
    if (myTemplate != null && myVelocityFileType != FileTypes.UNKNOWN) {
      return EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, new LightVirtualFile("aaa." + myTemplate.getExtension() + ".ft"));
    }

    FileType fileType = null;
    if (myTemplate != null) {
      fileType = FileTypeManager.getInstance().getFileTypeByExtension(myTemplate.getExtension());
    }
    if (fileType == null) {
      fileType = FileTypes.PLAIN_TEXT;
    }
    
    SyntaxHighlighter originalHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, null, null);
    if (originalHighlighter == null) {
      originalHighlighter = new PlainSyntaxHighlighter();
    }
    
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    LayeredLexerEditorHighlighter highlighter = new LayeredLexerEditorHighlighter(new TemplateHighlighter(), scheme);
    highlighter.registerLayer(FileTemplateTokenType.TEXT, new LayerDescriptor(originalHighlighter, ""));
    return highlighter;
  }

  private static class TemplateHighlighter extends SyntaxHighlighterBase {
    private final Lexer myLexer;

    public TemplateHighlighter() {
      myLexer = new MergingLexerAdapter(new FlexAdapter(new FileTemplateTextLexer()), TokenSet.create(FileTemplateTokenType.TEXT));
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      return myLexer;
    }

    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      if (tokenType == FileTemplateTokenType.MACRO || tokenType == FileTemplateTokenType.DIRECTIVE) {
        return pack(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
      }

      return EMPTY;
    }
  }


  public void focusToNameField() {
    myNameField.selectAll();
    myNameField.requestFocus();
  }

  public void focusToExtensionField() {
    myExtensionField.selectAll();
    myExtensionField.requestFocus();
  }
}
