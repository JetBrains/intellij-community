/**
 * @author cdr
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.macro.MacroUtil;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.javaee.web.WebUtil;
import com.intellij.lang.properties.PropertiesFilesManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class I18nizeQuickFixDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog");

  private JTextField myValue;
  private JComboBox myKey;
  private TextFieldWithHistory myPropertiesFile;
  private JPanel myPanel;
  private JCheckBox myUseResourceBundle;
  private final Project myProject;
  private final PsiFile myContext;
  private final PsiLiteralExpression myLiteralExpression;
  private JPanel myPropertiesFilePanel;
  private JLabel myPreviewLabel;
  private JPanel myHyperLinkPanel;
  private JPanel myResourceBundleSuggester;
  private EditorComboBox myRBEditorTextField;
  private JLabel _P;
  private JLabel _E;
  private JPanel myJavaCodeInfoPanel;
  private JPanel myPreviewPanel;
  private PsiClassType myResourceBundleType;
  private final String myDefaultPropertyValue;
  private final boolean myShowJavaCodeInfo;
  private final boolean myShowPreview;

  @NonNls private static final String PROPERTY_KEY_OPTION_KEY = "PROPERTY_KEY";
  @NonNls private static final String RESOURCE_BUNDLE_OPTION_KEY = "RESOURCE_BUNDLE";
  @NonNls public static final String PROPERTY_VALUE_ATTR = "PROPERTY_VALUE";

  public I18nizeQuickFixDialog(Project project,
                               @NotNull final PsiFile context,
                               @Nullable final PsiLiteralExpression literalExpression,
                               String defaultPropertyValue,
                               final boolean showJavaCodeInfo,
                               final boolean showPreview) {
    super(false);
    myProject = project;
    myContext = context;
    myLiteralExpression = literalExpression;

    myDefaultPropertyValue = defaultPropertyValue;
    myShowJavaCodeInfo = showJavaCodeInfo;
    myShowPreview = showPreview;

    setTitle(CodeInsightBundle.message("i18nize.dialog.title"));

    myResourceBundleSuggester.setLayout(new BorderLayout());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    PsiClass rbClass = psiManager.findClass("java.util.ResourceBundle", GlobalSearchScope.allScope(myProject));
    if (rbClass == null) {
      Messages.showErrorDialog(myProject,
                               CodeInsightBundle.message("i18nize.dialog.error.jdk.message"),
                               CodeInsightBundle.message("i18nize.dialog.error.jdk.title"));
      return;
    }
    PsiElementFactory factory = psiManager.getElementFactory();
    myResourceBundleType = factory.createType(rbClass);

    if (myShowJavaCodeInfo) {
      @NonNls String defaultVarName = "resourceBundle";
      PsiExpressionCodeFragment expressionCodeFragment = factory.createExpressionCodeFragment(defaultVarName, myLiteralExpression, myResourceBundleType, true);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(expressionCodeFragment);
      myRBEditorTextField = new EditorComboBox(document, myProject, StdFileTypes.JAVA);
      myResourceBundleSuggester.add(myRBEditorTextField, BorderLayout.CENTER);
      suggestAvailableResourceBundleExpressions();
      myRBEditorTextField.addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
        public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
          somethingChanged();
        }
      });
      _E.setLabelFor(myRBEditorTextField);
    }

    myPropertiesFile = new TextFieldWithHistory();
    JPanel panel = GuiUtils.constructFieldWithBrowseButton(myPropertiesFile, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(myProject);
        TreeFileChooser fileChooser = chooserFactory.createFileChooser(
          CodeInsightBundle.message("i18nize.dialog.property.file.chooser.title"), getPropertiesFile(),
          StdFileTypes.PROPERTIES, null);
        fileChooser.showDialog();
        PsiFile selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null) return;
        myPropertiesFile.setText(FileUtil.toSystemDependentName(selectedFile.getVirtualFile().getPath()));
      }
    });
    myPropertiesFilePanel.setLayout(new BorderLayout());
    myPropertiesFilePanel.add(panel, BorderLayout.CENTER);
    _P.setLabelFor(myPropertiesFile);
    populatePropertiesFiles();

    myPropertiesFile.addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        propertiesFileChanged();
        somethingChanged();
      }
    });

    getKeyTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        somethingChanged();
      }
    });

    myValue.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        somethingChanged();
      }
    });

    myHyperLinkPanel.setLayout(new BorderLayout());
    HyperlinkLabel link = new HyperlinkLabel(CodeInsightBundle.message("i18nize.dialog.template.link.label"));
    link.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        final FileTemplateConfigurable configurable = new FileTemplateConfigurable();
        final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(getTemplateName());
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            configurable.setTemplate(template, null);
          }
        });
        boolean ok = ShowSettingsUtil.getInstance().editConfigurable(myPanel, configurable);
        if (ok) {
          somethingChanged();
          if (myShowJavaCodeInfo) {
            suggestAvailableResourceBundleExpressions();
          }
        }
      }
    });
    myHyperLinkPanel.add(link, BorderLayout.CENTER);

    if (!myShowJavaCodeInfo) {
      myJavaCodeInfoPanel.setVisible(false);
    }
    if (!myShowPreview) {
      myPreviewPanel.setVisible(false);
    }

    propertiesFileChanged();
    somethingChanged();
    setKeyValueEditBoxes();

    init();
  }

  private JTextField getKeyTextField() {
    return (JTextField)myKey.getEditor().getEditorComponent();
  }

  protected String getTemplateName() {
    return FileTemplateManager.TEMPLATE_I18NIZED_EXPRESSION;
  }

  private void suggestAvailableResourceBundleExpressions() {
    if (myShowJavaCodeInfo) {
      FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(getTemplateName());
      boolean showResourceBundleSuggester = template.getText().contains("${" + RESOURCE_BUNDLE_OPTION_KEY + "}");
      //GuiUtils.enableChildren(myJavaCodeInfoPanel, showResourceBundleSuggester, null);
      myJavaCodeInfoPanel.setVisible(showResourceBundleSuggester);
    }
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(myLiteralExpression, "");
    Set<String> result = new LinkedHashSet<String>();
    for (PsiVariable var : variables) {
      PsiType varType = var.getType();
      if (myResourceBundleType == null || myResourceBundleType.isAssignableFrom(varType)) {
        result.add(var.getNameIdentifier().getText());
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressionsOfType(myLiteralExpression, myResourceBundleType);
    for (PsiExpression expression : expressions) {
      result.add(expression.getText());
    }
    if (myResourceBundleType != null) {
      addAvailableMethodsOfType(result, myResourceBundleType);
    }
    if (result.isEmpty()) {
      result.add(getResourceBundleText());
    }

    myRBEditorTextField.setHistory(result.toArray(new String[result.size()]));
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myRBEditorTextField.setSelectedIndex(0);
      }
    });
  }

  private void addAvailableMethodsOfType(final Collection<String> result, final PsiClassType type) {
    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          PsiType returnType = method.getReturnType();
          if (returnType != null
              && TypeConversionUtil.isAssignable(type, returnType)
              && method.getParameterList().getParameters().length == 0
            ) {
            result.add(method.getName()+"()");
          }
        }
        return true;
      }

      public <T> T getHint(Class<T> hintClass) {
        return null;
      }

      public void handleEvent(Event event, Object associated) {

      }
    }, myLiteralExpression, null);
  }

  @NotNull protected List<String> getExistingValueKeys(String value) {
    final ArrayList<String> result = new ArrayList<String>();

    // check if property value already exists among properties file values and suggest corresponding key
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      for (Property property : propertiesFile.getProperties()) {
        if (Comparing.strEqual(property.getValue(), value)) {
          result.add(0, property.getKey());
        }
      }
    }
    return result;
  }

  protected String suggestPropertyKey(String value) {
    // suggest property key not existing in this file
    final StringBuilder result = new StringBuilder();
    boolean insertDotBeforeNextWord = false;
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (insertDotBeforeNextWord) {
          result.append('.');
        }
        result.append(Character.toLowerCase(c));
        insertDotBeforeNextWord = false;
      }
      else if (c == '&') {   //do not insert dot if there is letter after the amp
        if (insertDotBeforeNextWord) continue;
        if (i == value.length() - 1) {
          continue;
        }
        if (Character.isLetter(value.charAt(i + 1))) {
          continue;
        }
        insertDotBeforeNextWord = true;
      }
      else  {
        if (result.length() > 0) {
          insertDotBeforeNextWord = true;
        }
      }
    }
    value = result.toString();

    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      if (propertiesFile.findPropertyByKey(value) == null) return value;

      int suffix=1;
      while (propertiesFile.findPropertyByKey(value + suffix) != null) {
        suffix++;
      }
      return value + suffix;
    } else {
      return value;
    }
  }

  private void propertiesFileChanged() {
    PropertiesFile propertiesFile = getPropertiesFile();
    boolean hasResourceBundle = propertiesFile != null && propertiesFile.getResourceBundle().getPropertiesFiles(propertiesFile.getProject()).size() > 1;
    myUseResourceBundle.setEnabled(hasResourceBundle);
    myUseResourceBundle.setSelected(hasResourceBundle);
  }

  private void setKeyValueEditBoxes() {
    final List<String> existingValueKeys = getExistingValueKeys(myDefaultPropertyValue);

    if (existingValueKeys.isEmpty()) {
      getKeyTextField().setText(suggestPropertyKey(myDefaultPropertyValue));
    }
    else {
      for (String key : existingValueKeys) {
        myKey.addItem(key);
      }
      myKey.setSelectedItem(existingValueKeys.get(0));
    }


    myValue.setText(myDefaultPropertyValue);
  }

  private void somethingChanged() {
    if (myShowPreview) {
      myPreviewLabel.setText(getI18nizedText());
    }
  }

  public String getI18nizedText() {
    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(getTemplateName());
    Map<String,String> attributes = new THashMap<String,String>();
    attributes.put(PROPERTY_KEY_OPTION_KEY, StringUtil.escapeStringCharacters(getKey()));
    attributes.put(RESOURCE_BUNDLE_OPTION_KEY, getResourceBundleText());
    addAdditionalAttributes(attributes);
    attributes.put(PROPERTY_VALUE_ATTR, StringUtil.escapeStringCharacters(myDefaultPropertyValue));
    String text = null;
    try {
      text = template.getText(attributes);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return text;
  }

  protected void addAdditionalAttributes(final Map<String, String> attributes) {
  }

  private void populatePropertiesFiles() {
    List<String> paths = suggestPropertiesFiles();
    Collections.sort(paths);
    myPropertiesFile.setHistory(paths);
    String lastUrl = suggestLastSelectedFileUrl();
    if (lastUrl != null) {
      String path = FileUtil.toSystemDependentName(VfsUtil.urlToPath(lastUrl));
      myPropertiesFile.setSelectedItem(path);
    }
    if (myPropertiesFile.getSelectedIndex() == -1 && !paths.isEmpty()) {
      myPropertiesFile.setText(paths.get(0));
    }
  }

  private String suggestLastSelectedFileUrl() {
    return LastSelectedPropertiesFileStore.getInstance().suggestLastSelectedPropertiesFileUrl(myContext);
  }
  private void saveLastSelectedFile() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      LastSelectedPropertiesFileStore.getInstance().saveLastSelectedPropertiesFile(myContext, propertiesFile);
    }
  }

  protected List<String> suggestPropertiesFiles() {
    Collection<VirtualFile> allPropertiesFiles = PropertiesFilesManager.getInstance().getAllPropertiesFiles();
    List<String> paths = new ArrayList<String>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (VirtualFile virtualFile : allPropertiesFiles) {
      if (projectFileIndex.isInSource(virtualFile) || WebUtil.isInsideWebRoots(virtualFile, myProject)) {
        String path = FileUtil.toSystemDependentName(virtualFile.getPath());
        paths.add(path);
      }
    }
    return paths;
  }

  private PropertiesFile getPropertiesFile() {
    String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile != null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) return (PropertiesFile)psiFile;
    }
    return null;
  }

  private boolean createPropertiesFileIfNotExists() {
    final String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(path);
    if (fileType != StdFileTypes.PROPERTIES) {
      Messages.showErrorDialog(myProject, "Can't create properties file '"+myPropertiesFile.getText()+"' because its name is associated with the "+fileType.getDescription() + ".", "Error creating properties file");
      return false;
    }

    final VirtualFile virtualFile;
    try {
      final File file = new File(path).getCanonicalFile();
      FileUtil.createParentDirs(file);
      final IOException[] e = new IOException[1];
      virtualFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>(){
        public VirtualFile compute() {
          VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getParentFile());
          try {
            if (dir == null) {
              throw new IOException("Error creating directory structure for file '" + path + "'");
            }
            return dir.createChildData(this, file.getName());
          }
          catch (IOException e1) {
            e[0] = e1;
          }
          return null;
        }
      });
      if (e[0] != null) throw e[0];
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject, e.getLocalizedMessage(), "Error creating properties file");
      return false;
    }

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    return psiFile instanceof PropertiesFile;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myKey;
  }

  public void dispose() {
    saveLastSelectedFile();
    super.dispose();
  }

  protected void doOKAction() {
    if (!createPropertiesFileIfNotExists()) return;
    Collection<PropertiesFile> propertiesFiles = getAllPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      Property existingProperty = propertiesFile.findPropertyByKey(getKey());
      final String propValue = myValue.getText();
      if (existingProperty != null && !Comparing.strEqual(existingProperty.getValue(), propValue)) {
        Messages.showErrorDialog(myProject, CodeInsightBundle.message("i18nize.dialog.error.property.already.defined.message", getKey(),
                                                                      propertiesFile.getName()),
                                            CodeInsightBundle.message("i18nize.dialog.error.property.already.defined.title"));
        return;
      }
    }

    super.doOKAction();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.propertyFile.i18nInspection");
  }


  public JComponent getValueComponent() {
    return myValue;
  }
  public String getValue() {
    return myValue.getText();
  }

  public String getKey() {
    return getKeyTextField().getText();
  }

  public boolean isUseResourceBundle() {
    return myUseResourceBundle.isSelected();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog";
  }

  public Collection<PropertiesFile> getAllPropertiesFiles() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile == null) return Collections.emptySet();
    Collection<PropertiesFile> propertiesFiles;
    if (isUseResourceBundle()) {
      propertiesFiles = propertiesFile.getResourceBundle().getPropertiesFiles(myProject);
    }
    else {
      propertiesFiles = Collections.singleton(propertiesFile);
    }
    return propertiesFiles;
  }

  public String getResourceBundleText() {
    return myShowJavaCodeInfo ? myRBEditorTextField.getText() : null;
  }

  public PsiLiteralExpression getLiteralExpression() {
    return myLiteralExpression;
  }
}
