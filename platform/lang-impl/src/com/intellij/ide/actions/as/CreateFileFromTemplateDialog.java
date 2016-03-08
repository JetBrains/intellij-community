/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions.as;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.as.CreateNewClassDialogValidatorEx;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class CreateFileFromTemplateDialog extends DialogWrapper {
  private static final String ATTRIBUTE_INTERFACES = "INTERFACES";
  private static final String ATTRIBUTE_VISIBILITY = "VISIBILITY";
  private static final String ATTRIBUTE_SUPERCLASS = "SUPERCLASS";
  private static final String ATTRIBUTE_FINAL = "FINAL";
  private static final String ATTRIBUTE_ABSTRACT = "ABSTRACT";
  private static final String ATTRIBUTE_IMPORT_BLOCK = "IMPORT_BLOCK";
  private static final String VISIBILITY_PACKAGE_PRIVATE = "visibility_package_private";
  private static final String VISIBILITY_PUBLIC = "visibility_public";

  private JPanel myPanel;
  private JLabel myNameLabel;
  private JTextField myNameField;
  private JLabel myUpDownHint;
  private JLabel myKindLabel;
  private TemplateKindCombo myKindCombo;
  private JLabel mySuperclassLabel;
  private JPanel mySuperclassFieldPlaceholder;
  private EditorTextField mySuperclassField;
  private JLabel myInterfacesLabel;
  private JPanel myInterfacesPanel;
  private JPanel myInterfacesFieldPlaceholder;
  private EditorTextField myInterfaceField;
  private JButton myAddInterfaceButton;
  private JLabel myPackageLabel;
  private JPanel myPackageFieldPlaceholder;
  private EditorTextField myPackageField;
  private JLabel myVisibilityLabel;
  private ButtonGroup myVisibilityButtonGroup;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myPackagePrivateRadioButton;
  private JCheckBox myAbstractCheckBox;
  private JCheckBox myFinalCheckBox;
  private JCheckBox myShowSelectOverridesDialogCheckBox;
  private JTextField mySelectedInterfacesField;

  private final Set<Type> mySelectedInterfaces;
  private ElementCreator myCreator;
  private CreateNewClassDialogValidatorEx myInputValidator;

  private final Project myProject;
  private final PsiPackage myDefaultPsiPackage;
  private final JavaCodeFragmentFactory myFragmentFactory;
  private final PsiDocumentManager myPsiDocumentManager;

  private final Map<String, String> myCreationOptions = new HashMap<String, String>();

  protected CreateFileFromTemplateDialog(@NotNull Project project, @NotNull PsiDirectory defaultDirectory) {
    super(project);

    setTitle(IdeBundle.message("action.create.new.class"));
    mySelectedInterfaces = new LinkedHashSet<Type>();
    myKindLabel.setLabelFor(myKindCombo);
    myVisibilityLabel.setLabelFor(myPublicRadioButton);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);

    myProject = project;
    myInputValidator = new CreateNewClassDialogValidatorExImpl(myProject);
    myDefaultPsiPackage =
      JavaPsiFacade.getInstance(project).findPackage(JavaDirectoryService.getInstance().getPackage(defaultDirectory).getQualifiedName());
    myFragmentFactory = JavaCodeFragmentFactory.getInstance(project);
    myPsiDocumentManager = PsiDocumentManager.getInstance(myProject);

    mySuperclassField = initAutocompleteEditorTextField("", "The superclass to explicitly extend, if any.");
    mySuperclassField.setName("superclass_editor_text_field");
    mySuperclassFieldPlaceholder.add(mySuperclassField);
    mySuperclassLabel.setLabelFor(mySuperclassField);
    myInterfaceField = initAutocompleteEditorTextField("", "A comma separated list of the interfaces to implement or extend, if any.");
    myInterfacesFieldPlaceholder.add(myInterfaceField);
    myInterfacesLabel.setLabelFor(myInterfaceField);
    myPackageField = initAutocompleteEditorTextField(myDefaultPsiPackage.getQualifiedName(), "The package to create the item in.");
    myPackageFieldPlaceholder.add(myPackageField);
    myPackageLabel.setLabelFor(myPackageField);

    setKindComponentsVisible(false);
    initVisibilityButtons();

    myAddInterfaceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        processNewInterface();
      }
    });

    init();
    initKindCombo();
  }

  /**
   * Gets a new interface from the input field, validates it, stores the input interface, and displays it as a selected one.
   */
  private void processNewInterface() {
    String interfaceAsString = myInterfaceField.getText();
    if (CharMatcher.WHITESPACE.matchesAllOf(interfaceAsString)) {
      myInterfaceField.requestFocus();
      return;
    }

    Type interfaceAsType = Type.newType(interfaceAsString, myProject);
    if (!interfaceAsType.canUseAsInterface() || !myInputValidator.checkInterface(interfaceAsString)) {
      startTrackingValidation();
      myInterfaceField.requestFocus();
      return;
    }

    // If the interface isn't in mySelectedInterfaces, add it and display it on the screen.
    if (mySelectedInterfaces.add(interfaceAsType)) {
      if (mySelectedInterfaces.size() == 1) {
        mySelectedInterfacesField.setText(interfaceAsType.getClassWithNesting());
      }
      else {
        mySelectedInterfacesField.setText(mySelectedInterfacesField.getText() + ", " + interfaceAsType.getClassWithNesting());
      }
    }

    myInterfaceField.setText("");
    myInterfaceField.requestFocus();
  }

  @NotNull
  private EditorTextField initAutocompleteEditorTextField(@NotNull String defaultText, @NotNull String tooltip) {
    JavaCodeFragment fragment = myFragmentFactory.createReferenceCodeFragment(defaultText, myDefaultPsiPackage, true, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    Document doc = myPsiDocumentManager.getDocument(fragment);
    EditorTextField editorTextField = new EditorTextField(doc, myProject, StdFileTypes.JAVA);
    editorTextField.setToolTipText(tooltip);
    return editorTextField;
  }

  private void initVisibilityButtons() {
    myPublicRadioButton.setActionCommand(VISIBILITY_PUBLIC);
    myPackagePrivateRadioButton.setActionCommand(VISIBILITY_PACKAGE_PRIVATE);

    myVisibilityButtonGroup = new ButtonGroup();
    myVisibilityButtonGroup.add(myPublicRadioButton);
    myVisibilityButtonGroup.add(myPackagePrivateRadioButton);
    myPublicRadioButton.setSelected(true);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myInputValidator != null) {
      String nameText = myNameField.getText();
      String superclassAsString = mySuperclassField.getText();
      String interfaceAsString = myInterfaceField.getText();
      String packageText = myPackageField.getText();
      if (!myInputValidator.checkInput(nameText)) {
        String errorText = LangBundle.message("incorrect.name");
        String message = myInputValidator.getErrorText(nameText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myNameField);
      }

      Type superclassAsType = Type.newType(superclassAsString, myProject);
      if (mySuperclassField.isVisible() && (!superclassAsType.canUseAsClass() || !myInputValidator.checkSuperclass(superclassAsString))) {
        String errorText = LangBundle.message("incorrect.superclass");
        String message = myInputValidator.getSuperclassErrorText(superclassAsString);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, mySuperclassField);
      }

      Type interfaceAsType = Type.newType(interfaceAsString, myProject);
      if (!interfaceAsType.canUseAsInterface() || !myInputValidator.checkInterface(interfaceAsString)) {
        String errorText = LangBundle.message("incorrect.name");
        String message = myInputValidator.getInterfacesErrorText(interfaceAsString);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myInterfaceField);
      }

      if (!myInputValidator.checkPackage(packageText)) {
        String errorText = LangBundle.message("incorrect.package");
        String message = myInputValidator.getPackageErrorText(packageText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myPackageField);
      }

      if (myAbstractCheckBox.isVisible() && myFinalCheckBox.isVisible() &&
          !myInputValidator.checkAbstractAndFinal(myAbstractCheckBox.isSelected(), myFinalCheckBox.isSelected())) {
        String errorText = LangBundle.message("incorrect.abstract.and.final");
        String message = myInputValidator.getAbstractAndFinalErrorText("");
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myFinalCheckBox);
      }
    }
    return super.doValidate();
  }

  protected void configureComponents(Kind kind) {
    switch (kind) {
      case ANNOTATION:
      case INTERFACE:
      case ENUM:
        mySuperclassLabel.setVisible(false);
        mySuperclassField.setText("");
        mySuperclassField.setFocusable(false);
        mySuperclassFieldPlaceholder.setVisible(false);
        myAbstractCheckBox.setSelected(false);
        myAbstractCheckBox.setVisible(false);
        myFinalCheckBox.setSelected(false);
        myFinalCheckBox.setVisible(false);
        myShowSelectOverridesDialogCheckBox.setSelected(false);
        myShowSelectOverridesDialogCheckBox.setVisible(false);
        break;
      case CLASS:
      case SINGLETON:
        mySuperclassLabel.setVisible(true);
        mySuperclassFieldPlaceholder.setVisible(true);
        mySuperclassField.setFocusable(true);
        myAbstractCheckBox.setVisible(true);
        myFinalCheckBox.setVisible(true);
        myShowSelectOverridesDialogCheckBox.setVisible(true);
        break;
    }
  }

  Map<String, String> getCreationOptions() {
    return myCreationOptions;
  }

  protected JTextField getNameField() {
    return myNameField;
  }

  protected TemplateKindCombo getKindCombo() {
    return myKindCombo;
  }

  protected JLabel getKindLabel() {
    return myKindLabel;
  }

  protected JLabel getNameLabel() {
    return myNameLabel;
  }

  private String getName() {
    String text = myNameField.getText().trim();
    myNameField.setText(text);
    return text;
  }

  public String getSuperclass() {
    String superclass = mySuperclassField.getText().trim();
    mySuperclassField.setText(superclass);
    return superclass;
  }

  public void setSuperclass(String superclass) {
    mySuperclassField.setText(superclass);
  }

  private String getPackage() {
    String packageName = myPackageField.getText().replace(" ", "");
    myPackageField.setText(packageName);
    return packageName;
  }

  public void setPackage(String packageName) {
    myPackageField.setText(packageName);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    processNewInterface();
    List<String> imports = new ArrayList<String>();
    String localPackage = getPackage();
    String superclassAsString = getSuperclass();
    if (!superclassAsString.isEmpty()) {
      Type superclassAsType = Type.newType(superclassAsString, myProject);
      myCreationOptions.put(ATTRIBUTE_SUPERCLASS, superclassAsType.getClassWithNesting());
      if (superclassAsType.requiresImport(localPackage)) {
        imports.add(superclassAsType.getClassToImport());
      }
    }
    else {
      myCreationOptions.put(ATTRIBUTE_SUPERCLASS, "");
    }

    // There are three types of interfaces to deal with: those local to the new file's package (locals), those that are not local, but
    // are fully qualified (non-locals), and those that are neither local nor fully qualified (mysteries).
    // We treat the mysteries as if they're local, and let the editor show an error later.
    // To start, we separate the interfaces into two groups: locals+mysteries and non-locals.
    // Next we add the locals/mysteries to the interfaces set, without qualification or imports.
    // Then we add the non-locals to the set. If there's already an entry with the same name, we add it fully qualified. Otherwise we add
    // it without qualification and add an import line.
    // When possible we use a Psi* backed class to parse the interfaces, to allow for extra validation. The mysteries require a string
    // backed class, because the Psi system doesn't have any data on them.
    Set<String> packageLocalInterfaces = new LinkedHashSet<String>();
    Set<Type> qualifiedInterfaces = new LinkedHashSet<Type>();
    for (Type selectedInterface : mySelectedInterfaces) {
      if (selectedInterface.isLocal(localPackage)) {
        packageLocalInterfaces.add(selectedInterface.getClassWithNesting());
      }
      else {
        qualifiedInterfaces.add(selectedInterface);
      }
    }

    Set<String> interfaces = new LinkedHashSet<String>();
    interfaces.addAll(packageLocalInterfaces);

    for (Type qualifiedInterface : qualifiedInterfaces) {
      if (interfaces.add(qualifiedInterface.getClassWithNesting())) {
        if (qualifiedInterface.requiresImport(localPackage)) {
          imports.add(qualifiedInterface.getClassToImport());
        }
      }
      else {
        interfaces.add(qualifiedInterface.getQualifiedClass());
      }
    }

    myCreationOptions.put(ATTRIBUTE_INTERFACES, Joiner.on(", ").join(interfaces));
    myCreationOptions.put(FileTemplate.ATTRIBUTE_PACKAGE_NAME, localPackage);
    Visibility visibility = myPublicRadioButton.isSelected() ? Visibility.PUBLIC : Visibility.PACKAGE_PRIVATE;
    myCreationOptions.put(ATTRIBUTE_VISIBILITY, visibility.toString());
    myCreationOptions.put(ATTRIBUTE_ABSTRACT, Boolean.toString(myAbstractCheckBox.isSelected()).toUpperCase(Locale.ROOT));
    myCreationOptions.put(ATTRIBUTE_FINAL, Boolean.toString(myFinalCheckBox.isSelected()).toUpperCase(Locale.ROOT));
    myCreationOptions.put(ATTRIBUTE_IMPORT_BLOCK, formatImports(imports));
    if (myCreator != null && myCreator.tryCreate(getName()).length == 0) {
      return;
    }

    super.doOKAction();
  }

  @NotNull
  private static String formatImports(Iterable<String> imports) {
    StringBuilder importBlock = new StringBuilder();
    for (String entry : imports) {
      importBlock.append("import ").append(entry).append(";\n");
    }

    return importBlock.toString();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getNameField();
  }

  private void setKindComponentsVisible(boolean visible) {
    myKindCombo.setVisible(visible);
    myKindLabel.setVisible(visible);
    myUpDownHint.setVisible(visible);
  }

  private void addKind(@NotNull Kind kind) {
    getKindCombo().addItem(kind.getName(), kind.getIcon(), kind.getTemplateName());
    if (getKindCombo().getComboBox().getItemCount() > 1) {
      setKindComponentsVisible(true);
    }
  }

  PsiClass show(@NotNull final FileCreator creator) throws FailedToCreateFileException {
    final Ref<PsiClass> ref = Ref.create(null);
    myCreator = new ElementCreator(myProject, IdeBundle.message("title.cannot.create.class")) {
      @Override
      protected PsiElement[] create(String newName) throws Exception {
        PsiClass element = creator.createFile(getName(), myCreationOptions, myKindCombo.getSelectedName());
        ref.set(element);
        return element == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{element};
      }

      @Override
      protected String getActionName(String newName) {
        return creator.getActionName(newName, myKindCombo.getSelectedName());
      }
    };

    show();
    if (getExitCode() == OK_EXIT_CODE) {
      return ref.get();
    }
    else {
      throw new FailedToCreateFileException("Create returned a null object.");
    }
  }

  boolean isShowSelectOverridesDialogCheckBoxSelected() {
    return myShowSelectOverridesDialogCheckBox.isSelected();
  }

  String getInterface() {
    return myInterfaceField.getText();
  }

  void setInterface(String newInterface) {
    myInterfaceField.setText(newInterface);
  }

  public void initKindCombo() {
    myKindCombo.registerUpDownHint(myNameField);
    myKindCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (actionEvent.getSource().equals(myKindCombo.getComboBox())) {
          configureComponents(Kind.valueOfText(myKindCombo.getSelectedName()));
        }
      }
    });

    addKind(Kind.CLASS);
    addKind(Kind.INTERFACE);
    if (LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) {
      addKind(Kind.ENUM);
      addKind(Kind.ANNOTATION);
    }

    addKind(Kind.SINGLETON);
  }

  interface FileCreator {
    @Nullable
    PsiClass createFile(@NotNull String name, @NotNull Map<String, String> creationOptions, @NotNull String templateName);

    @NotNull
    String getActionName(@NotNull String name, @NotNull String templateName);
  }

  public enum Visibility {
    PUBLIC,
    PACKAGE_PRIVATE
  }

  public static abstract class Type {
    private static final String JAVA_LANG_PACKAGE = "java.lang";

    abstract String getClassWithNesting();

    abstract String getClassToImport();

    abstract String getPackage();

    abstract String getQualifiedClass();

    abstract boolean canUseAsClass();

    abstract boolean canUseAsInterface();

    boolean isLocal(String localPackage) {
      return getPackage().equals(localPackage) || getPackage().isEmpty();
    }

    private static Type newType(@NotNull String qualifiedName, @NotNull Project project) {
      try {
        return new PsiBackedType(qualifiedName, project);
      }
      catch (IllegalArgumentException e) {
        return new StringBackedType(qualifiedName);
      }
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof Type) {
        Type qualifiedClass = (Type)object;
        return getClassWithNesting().equals(qualifiedClass.getClassWithNesting()) &&
               getPackage().equals(qualifiedClass.getPackage());
      }

      return false;
    }

    @Override
    public int hashCode() {
      int hashCode = 17;
      hashCode = 31 * hashCode + getClassWithNesting().hashCode();
      hashCode = 31 * hashCode + getPackage().hashCode();
      return hashCode;
    }

    private boolean requiresImport(String localPackage) {
      return !getPackage().equals(localPackage) && !getPackage().equals(JAVA_LANG_PACKAGE);
    }
  }

  private static class PsiBackedType extends Type {
    private final PsiClass myPsiClass;
    private final PsiPackage myPsiPackage;
    private final JavaDirectoryService myJavaDirectoryService;
    private final String myClassNameWithNesting;
    private final String myNameOfClassToImport;

    private PsiBackedType(@NotNull String className, @NotNull Project project) {
      myJavaDirectoryService = JavaDirectoryService.getInstance();
      myPsiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      if (myPsiClass == null) {
        throw new IllegalArgumentException(className);
      }

      PsiPackage psiPackage = null;
      for (PsiElement parent = myPsiClass.getParent(); parent != null; parent = parent.getParent()) {
        if (parent instanceof PsiDirectory) {
          PsiDirectory psiDirectory = (PsiDirectory)parent;
          psiPackage = myJavaDirectoryService.getPackage(psiDirectory);
          break;
        }
      }

      String classToImport = null;
      Deque<String> containingClasses = new ArrayDeque<String>();
      for (PsiClass psiClass = myPsiClass; psiClass != null; psiClass = psiClass.getContainingClass()) {
        classToImport = psiClass.getName();
        containingClasses.addFirst(psiClass.getName());
      }

      myClassNameWithNesting = Joiner.on(".").join(containingClasses);
      myPsiPackage = psiPackage;
      myNameOfClassToImport = classToImport;
    }

    @Override
    String getClassWithNesting() {
      return myClassNameWithNesting;
    }

    @Override
    String getClassToImport() {
      return myPsiPackage.getQualifiedName() + "." + myNameOfClassToImport;
    }

    @Override
    String getPackage() {
      if (myPsiPackage != null) {
        return myPsiPackage.getQualifiedName();
      }
      else {
        throw new IllegalStateException("myPsiPackage cannot be null for a PsiBackedType.");
      }
    }

    @Override
    String getQualifiedClass() {
      return myPsiClass.getQualifiedName();
    }

    @Override
    boolean canUseAsClass() {
      return !myPsiClass.isInterface() && !myPsiClass.isEnum() && !myPsiClass.isAnnotationType();
    }

    @Override
    boolean canUseAsInterface() {
      return myPsiClass.isInterface();
    }
  }

  private static class StringBackedType extends Type {
    private final String myPackage;
    private final String myClass;

    private StringBackedType(@NotNull String className) {
      int lastDotIndex = className.lastIndexOf(".");
      if (lastDotIndex != -1) {
        myPackage = className.substring(0, lastDotIndex);
        myClass = className.substring(lastDotIndex + 1);
      }
      else {
        myPackage = "";
        myClass = className;
      }
    }

    @NotNull
    @Override
    String getClassWithNesting() {
      return myClass;
    }

    @Override
    String getClassToImport() {
      return getQualifiedClass();
    }

    @NotNull
    @Override
    String getPackage() {
      return myPackage;
    }

    @NotNull
    @Override
    String getQualifiedClass() {
      return myPackage.isEmpty() ? myClass : myPackage + "." + myClass;
    }

    @Override
    boolean canUseAsClass() {
      return true;
    }

    @Override
    boolean canUseAsInterface() {
      return true;
    }
  }

  static class FailedToCreateFileException extends Exception {
    FailedToCreateFileException(String message) {
      super(message);
    }
  }
}
