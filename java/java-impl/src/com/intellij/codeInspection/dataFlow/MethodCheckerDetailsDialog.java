/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.*;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;
import java.util.List;

import static com.intellij.codeInsight.ConditionChecker.Type.*;

/**
 * Dialog that appears when the user clicks the Add Button or double clicks a row item in a MethodsPanel.  The MethodsPanel is accessed from the ConditionCheckDialog
 */
class MethodCheckerDetailsDialog extends DialogWrapper implements PropertyChangeListener, ItemListener {
  @NotNull private final ConditionChecker.Type myType;
  @NotNull private final Project myProject;
  @NotNull private final ParameterDropDown parameterDropDown;
  @NotNull private final MethodDropDown methodDropDown;
  @NotNull private final ClassField classField;
  @NotNull private final Set<ConditionChecker> myOtherCheckers;
  @Nullable private final ConditionChecker myPreviouslySelectedChecker;
  /**
   * Set by the OK and/or Cancel actions so that the caller can retrieve it via a call to getMethodIsNullIsNotNullChecker
   */
  @Nullable private ConditionChecker mySelectedChecker;

  MethodCheckerDetailsDialog(@Nullable ConditionChecker previouslySelectedChecker,
                             @NotNull ConditionChecker.Type type,
                             @NotNull Project project,
                             @NotNull Component component,
                             @NotNull Set<ConditionChecker> otherCheckersSameType,
                             @NotNull Set<ConditionChecker> otherCheckers) {
    super(component, true);
    if (!isSupported(type)) throw new IllegalArgumentException("Type is invalid " + type);

    myProject = project;
    myType = type;
    myOtherCheckers = new HashSet<ConditionChecker>(otherCheckersSameType);
    myOtherCheckers.addAll(otherCheckers);
    myPreviouslySelectedChecker = previouslySelectedChecker;
    if (myPreviouslySelectedChecker != null) myOtherCheckers.remove(myPreviouslySelectedChecker);

    PsiClass psiClass = null;
    PsiMethod psiMethod = null;
    PsiParameter psiParameter = null;
    if (previouslySelectedChecker != null) {
      psiClass =
        JavaPsiFacade.getInstance(myProject).findClass(previouslySelectedChecker.getClassName(), GlobalSearchScope.allScope(myProject));
      if (psiClass != null) {
        for (PsiMethod method : psiClass.findMethodsByName(previouslySelectedChecker.getMethodName(), true)) {
          if (previouslySelectedChecker.equals(buildParameterClassListFromPsiMethod(method))) {
            psiMethod = method;
            break;
          }
        }
      }

      if (psiMethod != null) {
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        if (parameters.length - 1 >= previouslySelectedChecker.getCheckedParameterIndex()) {
          psiParameter = parameters[previouslySelectedChecker.getCheckedParameterIndex()];
        }
      }
    }

    if (psiClass == null || psiMethod == null || psiParameter == null) {
      psiClass = null;
      psiMethod = null;
      psiParameter = null;
    }

    classField = new ClassField(myProject, psiClass);
    methodDropDown = new MethodDropDown(psiClass, psiMethod, myType, MethodDropDown.buildModel());
    parameterDropDown = new ParameterDropDown(psiMethod, psiParameter, ParameterDropDown.buildModel(), myType);
    classField.addPropertyChangeListener(methodDropDown);
    classField.addPropertyChangeListener(parameterDropDown);
    classField.addPropertyChangeListener(this);
    methodDropDown.addItemListener(parameterDropDown);
    methodDropDown.addItemListener(this);
    parameterDropDown.addItemListener(this);
    init();
    checkOkActionEnable();
    setTitle(initTitle(type));
  }

  private static boolean isSupported(ConditionChecker.Type type) {
    return type == IS_NULL_METHOD || type == IS_NOT_NULL_METHOD ||
           type == ASSERT_IS_NULL_METHOD || type == ASSERT_IS_NOT_NULL_METHOD ||
           type == ASSERT_TRUE_METHOD || type == ASSERT_FALSE_METHOD;
  }

  private static List<String> buildParameterClassListFromPsiMethod(PsiMethod psiMethod) {
    List<String> parameterClasses = new ArrayList<String>();
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    for (PsiParameter param : parameters) {
      PsiTypeElement typeElement = param.getTypeElement();
      if (typeElement == null) return new ArrayList<String>();

      PsiType psiType = typeElement.getType();

      parameterClasses.add(psiType.getCanonicalText());
    }
    return parameterClasses;
  }

  private static String initTitle(@NotNull ConditionChecker.Type type) {
    if (type.equals(IS_NULL_METHOD)) {
      return InspectionsBundle.message("configure.checker.option.isNull.add.method.checker.dialog.title");
    }
    else if (type.equals(IS_NOT_NULL_METHOD)) {
      return InspectionsBundle.message("configure.checker.option.isNotNull.add.method.checker.dialog.title");
    }
    else if (type.equals(ASSERT_IS_NULL_METHOD)) {
      return InspectionsBundle.message("configure.checker.option.assert.isNull.add.method.checker.dialog.title");
    }
    else if (type.equals(ASSERT_IS_NOT_NULL_METHOD)) {
      return InspectionsBundle.message("configure.checker.option.assert.isNotNull.add.method.checker.dialog.title");
    }
    else if (type.equals(ASSERT_TRUE_METHOD)) {
      return InspectionsBundle.message("configure.checker.option.assert.true.add.method.checker.dialog.title");
    }
    else if (type.equals(ASSERT_FALSE_METHOD)) {
      return InspectionsBundle.message("configure.checker.option.assert.false.add.method.checker.dialog.title");
    }
    else {
      throw new IllegalArgumentException("MethodCheckerDetailsDialog does not support type " + type);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    final LabeledComponent<ClassField> classComponent = new LabeledComponent<ClassField>();
    final LabeledComponent<MethodDropDown> methodComponent = new LabeledComponent<MethodDropDown>();
    final LabeledComponent<ParameterDropDown> parameterComponent = new LabeledComponent<ParameterDropDown>();

    classComponent.setText("Class");
    methodComponent.setText("Method");
    parameterComponent.setText("Parameter");

    classComponent.setComponent(classField);
    methodComponent.setComponent(methodDropDown);
    parameterComponent.setComponent(parameterDropDown);

    panel.add(classComponent);
    panel.add(methodComponent);
    panel.add(parameterComponent);

    return panel;
  }

  @Nullable
  ConditionChecker getConditionChecker() {
    return mySelectedChecker;
  }

  @Nullable
  private ConditionChecker buildConditionChecker() {
    PsiClass psiClass = classField.getPsiClass();
    PsiMethod psiMethod = methodDropDown.getSelectedPsiMethod();
    PsiParameter psiParameter = parameterDropDown.getSelectedPsiParameter();
    if (psiClass != null && psiMethod != null && psiParameter != null) {
      return new ConditionChecker.FromPsiBuilder(psiMethod, psiParameter, myType).build();
    }
    else {
      return null;
    }
  }

  private boolean overlaps(ConditionChecker thisChecker) {
    for (ConditionChecker overlappingChecker : myOtherCheckers) {
      if (thisChecker.overlaps(overlappingChecker)) {
        Messages.showMessageDialog(myProject, InspectionsBundle.message("configure.checker.option.overlap.error.msg") +
                                              " " +
                                              overlappingChecker.getConditionCheckType() + " " + overlappingChecker.toString(),
                                   InspectionsBundle.message("configure.checker.option.overlap.error.title"), Messages.getErrorIcon());
        return true;
      }
    }
    return false;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    checkOkActionEnable();
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    checkOkActionEnable();
  }

  private void checkOkActionEnable() {
    if (classField.getPsiClass() == null ||
        methodDropDown.getSelectedPsiMethod() == null ||
        parameterDropDown.getSelectedPsiParameter() == null) {
      setOKActionEnabled(false);
    }
    else {
      setOKActionEnabled(true);
    }
  }

  @Override
  protected void doOKAction() {
    ConditionChecker checker = buildConditionChecker();
    if (checker != null && !overlaps(checker)) {
      if (checker.equals(myPreviouslySelectedChecker)) {
        mySelectedChecker = myPreviouslySelectedChecker;
      }
      else {
        mySelectedChecker = checker;
      }
      super.doOKAction();
    }
  }

  @Override
  public boolean isOKActionEnabled() {
    if (!myOKAction.isEnabled()) return false;
    PsiClass psiClass = classField.getPsiClass();
    PsiMethod psiMethod = methodDropDown.getSelectedPsiMethod();
    PsiParameter psiParameter = parameterDropDown.getSelectedPsiParameter();
    if (psiClass == null || psiMethod == null || psiParameter == null) {
      return false;
    }
    else {
      return true;
    }
  }

  /**
   * Input Text Field for Class Name
   */
  static class ClassField extends EditorTextFieldWithBrowseButton implements ActionListener, DocumentListener {
    public static final String PROPERTY_PSICLASS = "ClassField.myPsiClass";
    @NotNull private final Project myProject;
    @Nullable private PsiClass myPsiClass;

    public ClassField(@NotNull Project project, @Nullable PsiClass psiClass) {
      super(project, true, buildVisibilityChecker());
      myProject = project;
      myPsiClass = psiClass;
      setPreferredSize(new Dimension(500, (int)getPreferredSize().getHeight()));
      if (myPsiClass != null) { //noinspection ConstantConditions
        setText(myPsiClass.getQualifiedName());
      }
      addActionListener(this);
      getChildComponent().addDocumentListener(this);
    }

    private static JavaCodeFragment.VisibilityChecker buildVisibilityChecker() {
      return new JavaCodeFragment.VisibilityChecker() {
        @Override
        public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
          return Visibility.VISIBLE;
        }
      };
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
        .createNoInnerClassesScopeChooser("Choose Class", GlobalSearchScope.allScope(myProject), new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return !aClass.isAnnotationType();
          }
        }, null);
      chooser.showDialog();
      PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) { //noinspection ConstantConditions
        setText(chooser.getSelected().getQualifiedName());
      }
    }

    @Nullable
    public PsiClass getPsiClass() {
      return myPsiClass;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      String className = event.getDocument().getText();
      PsiClass psiClass = null;
      if (className != null) {
        psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
      }

      if (psiClass != null && myPsiClass != null) {
        if (!psiClass.equals(myPsiClass)) {
          firePropertyChange(PROPERTY_PSICLASS, myPsiClass, psiClass);
          myPsiClass = psiClass;
        }
      }
      else if (psiClass != null) {
        firePropertyChange(PROPERTY_PSICLASS, myPsiClass, psiClass);
        myPsiClass = psiClass;
      }
      else if (myPsiClass != null) {
        firePropertyChange(PROPERTY_PSICLASS, myPsiClass, psiClass);
        myPsiClass = null;
      }
    }
  }

  /**
   * Drop Down for picking Method Name
   */
  static class MethodDropDown extends JComboBox implements PropertyChangeListener {
    @NotNull private final ConditionChecker.Type myType;
    @NotNull private final SortedComboBoxModel<MethodWrapper> myModel;
    @Nullable private PsiClass myPsiClass;

    MethodDropDown(@Nullable PsiClass psiClass,
                   @Nullable PsiMethod psiMethod,
                   @NotNull ConditionChecker.Type type,
                   @NotNull SortedComboBoxModel<MethodWrapper> model) {
      super(model);

      if (!isSupported(type)) throw new IllegalArgumentException("Type is invalid " + type);

      myPsiClass = psiClass;
      myType = type;
      myModel = model;
      setEnabled(myPsiClass != null);
      initValues();
      if (psiMethod != null) {
        for (Iterator<MethodWrapper> iterator = myModel.iterator(); iterator.hasNext(); ) {
          MethodWrapper methodWrapper = iterator.next();
          if (methodWrapper.getPsiMethod().equals(psiMethod)) {
            setSelectedItem(methodWrapper);
          }
        }
      }
    }

    private static boolean isMethodFromJavaLangObject(PsiMethod method) {
      if (method == null) return false;

      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return false;
      String name = containingClass.getQualifiedName();
      if (name == null) return false;

      if (CommonClassNames.JAVA_LANG_OBJECT.equals(name)) return true;

      return false;
    }

    @NotNull
    public static SortedComboBoxModel<MethodWrapper> buildModel() {
      return new SortedComboBoxModel<MethodWrapper>(new Comparator<MethodWrapper>() {
        @Override
        public int compare(MethodWrapper o1, MethodWrapper o2) {
          return o1.compareTo(o2);
        }
      });
    }

    private void initValues() {
      if (myPsiClass != null) {
        myModel.clear();
        myModel.setSelectedItem(null);
        PsiMethod[] allMethods = myPsiClass.getAllMethods();
        for (PsiMethod method : allMethods) {
          MethodWrapper methodWrapper = new MethodWrapper(method);
          if (qualifies(method) && !myModel.getItems().contains(methodWrapper)) myModel.add(methodWrapper);
        }
      }
    }

    public boolean qualifies(PsiMethod psiMethod) {
      if (isMethodFromJavaLangObject(psiMethod)) {
        return false;
      }

      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      if (parameters.length < 1) {
        return false;
      }

      if (myType == IS_NULL_METHOD || myType == IS_NOT_NULL_METHOD) {
        PsiType returnType = psiMethod.getReturnType();
        if (returnType != PsiType.BOOLEAN && (returnType == null || !returnType.getCanonicalText().equals(Boolean.class.toString()))) {
          return false;
        }
      }
      else if (myType == ASSERT_TRUE_METHOD || myType == ASSERT_FALSE_METHOD) {
        boolean booleanParamExists = false;
        for (PsiParameter psiParameter : parameters) {
          PsiType type = psiParameter.getType();
          if (type.equals(PsiType.BOOLEAN) || type.getCanonicalText().equals(Boolean.class.toString())) {
            booleanParamExists = true;
            break;
          }
        }

        if (!booleanParamExists) {
          return false;
        }
      }
      // Else it's ASSERT_IS_NULL_METHOD or ASSERT_IS_NOT_NULL_METHOD.
      // In that case there is no additional validation

      return true;
    }

    /**
     * Called when ClassField is set and when user selects entry in the MethodDropDown
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(ClassField.PROPERTY_PSICLASS)) {
        if (evt.getNewValue() == null) {
          clear();
        }
        else {
          setEnabled(true);
          if (myPsiClass == null || !myPsiClass.equals(evt.getNewValue())) { // ClassChanged so refresh list
            myPsiClass = (PsiClass)evt.getNewValue();
            initValues();
          }
        }
      }
    }

    public void clear() {
      myModel.clear();
      myModel.setSelectedItem(null);
      setEnabled(false);
      myPsiClass = null;
    }

    @Nullable
    public PsiMethod getSelectedPsiMethod() {
      MethodWrapper methodWrapper = myModel.getSelectedItem();
      if (methodWrapper == null) return null;

      return methodWrapper.getPsiMethod();
    }
  }

  /**
   * Drop Down for picking Parameter Name
   */
  static class ParameterDropDown extends JComboBox implements PropertyChangeListener, ItemListener {
    @NotNull private final SortedComboBoxModel<ParameterWrapper> myModel;
    @NotNull private final ConditionChecker.Type myType;
    @Nullable private PsiMethod myPsiMethod;

    public ParameterDropDown(@Nullable PsiMethod psiMethod,
                             @Nullable PsiParameter psiParameter,
                             @NotNull SortedComboBoxModel<ParameterWrapper> model,
                             @NotNull ConditionChecker.Type type) {
      super(model);

      if (!isSupported(type)) throw new IllegalArgumentException("Type is invalid " + type);

      myPsiMethod = psiMethod;
      myModel = model;
      myType = type;

      if (myPsiMethod != null) {
        setEnabled(true);
        myModel.addAll(getParameterWrappers());
        if (psiParameter != null) {
          for (Iterator iterator = myModel.iterator(); iterator.hasNext(); ) {
            ParameterWrapper wrapper = (ParameterWrapper)iterator.next();
            if (wrapper.getPsiParameter().equals(psiParameter)) setSelectedItem(wrapper);
          }
        }
      }
      else {
        setEnabled(false);
      }
    }

    public static SortedComboBoxModel<ParameterWrapper> buildModel() {
      return new SortedComboBoxModel<ParameterWrapper>(new Comparator<ParameterWrapper>() {
        @Override
        public int compare(ParameterWrapper o1, ParameterWrapper o2) {
          return o1.compareTo(o2);
        }
      });
    }

    List<ParameterWrapper> getParameterWrappers() {
      List<ParameterWrapper> wrappers = new ArrayList<ParameterWrapper>();
      if (myPsiMethod != null) {
        PsiParameterList parameterList = myPsiMethod.getParameterList();
        for (int i = 0; i < parameterList.getParameters().length; i++) {
          PsiParameter psiParameter = parameterList.getParameters()[i];
          if (myType == ASSERT_TRUE_METHOD || myType == ASSERT_FALSE_METHOD) {
            PsiType type = psiParameter.getType();
            if (type.equals(PsiType.BOOLEAN) || type.getCanonicalText().equals(Boolean.class.toString())) {
              wrappers.add(new ParameterWrapper(psiParameter, i));
            }
          }
          else {
            wrappers.add(new ParameterWrapper(psiParameter, i));
          }

        }
      }
      return wrappers;
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
      if (e.getSource() instanceof MethodDropDown) { // The MethodDropDown has changed.
        MethodDropDown methodDropDown = (MethodDropDown)e.getSource();
        if (methodDropDown.getSelectedPsiMethod() != null) {
          setEnabled(true);
          if (myPsiMethod == null || !myPsiMethod.equals(methodDropDown.getSelectedPsiMethod())) {
            myPsiMethod = methodDropDown.getSelectedPsiMethod();
            myModel.clear();
            myModel.addAll(getParameterWrappers());
            myModel.setSelectedItem(null);
          }
        }
        else {
          myPsiMethod = null;
          myModel.clear();
          myModel.setSelectedItem(null);
          setEnabled(false);
        }
      }
      else {
        throw new RuntimeException("Unexpected Configuration ParameterDropDown is only expected to receive events from MethodDropDown.");
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(ClassField.PROPERTY_PSICLASS)) {
        if (evt.getNewValue() == null) {
          setEnabled(false);
        }
      }
    }

    @Nullable
    public PsiParameter getSelectedPsiParameter() {
      ParameterWrapper parameterWrapper = myModel.getSelectedItem();
      if (parameterWrapper == null) return null;

      return parameterWrapper.getPsiParameter();
    }

    class ParameterWrapper implements Comparable<ParameterWrapper> {
      @NotNull private final String id;
      @NotNull private final PsiParameter psiParameter;
      private final int index;

      ParameterWrapper(@NotNull PsiParameter psiParameter, int index) {
        this.psiParameter = psiParameter;
        this.index = index;
        String typeName;
        PsiTypeElement typeElement = psiParameter.getTypeElement();
        if (typeElement == null) {
          typeName = "";
        }
        else {
          if (typeElement.getType() instanceof PsiPrimitiveType) {
            typeName = ((PsiPrimitiveType)typeElement.getType()).getBoxedTypeName();
          }
          else {
            typeName = typeElement.getType().getCanonicalText();
          }
        }

        id = typeName + " " + psiParameter.getName();
      }

      @Override
      public int compareTo(ParameterWrapper o) {
        return index - o.index;
      }

      @Override
      public String toString() {
        return id;
      }

      @NotNull
      public PsiParameter getPsiParameter() {
        return psiParameter;
      }
    }
  }

  static class MethodWrapper implements Comparable<MethodWrapper> {
    @NotNull private final PsiMethod myPsiMethod;
    @NotNull private final String myId;

    MethodWrapper(@NotNull PsiMethod psiMethod) {
      this.myPsiMethod = psiMethod;

      List<String> parameterClassNames = new ArrayList<String>();
      PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      for (PsiParameter psiParameter : parameters) {
        parameterClassNames.add(getParameterQualifiedName(psiParameter));
      }

      myId = initId(psiMethod.getName(), parameterClassNames);
    }

    private static String getParameterQualifiedName(PsiParameter psiParameter) {
      PsiTypeElement typeElement = psiParameter.getTypeElement();
      if (typeElement == null) {
        return "";
      }

      if (typeElement.getType() instanceof PsiPrimitiveType) {
        return ((PsiPrimitiveType)typeElement.getType()).getBoxedTypeName();
      }

      return typeElement.getType().getCanonicalText();
    }

    private static String initId(String methodName, List<String> parameterNames) {
      String shortName = methodName + "(";
      for (String parameterName : parameterNames) {
        if (parameterNames.lastIndexOf(".") > -1) {
          shortName += parameterName.substring(parameterName.lastIndexOf(".") + 1) + ", ";
        }
        else {
          shortName += parameterName + ", ";
        }
      }

      if (parameterNames.size() > 0) shortName = shortName.substring(0, shortName.lastIndexOf(", "));

      shortName += ")";
      return shortName;
    }

    @NotNull
    public PsiMethod getPsiMethod() {
      return myPsiMethod;
    }

    @NotNull
    public String getId() {
      return myId;
    }

    @Override
    public String toString() {
      return myId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MethodWrapper that = (MethodWrapper)o;

      if (!myId.equals(that.myId)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myId.hashCode();
    }

    @Override
    public int compareTo(MethodWrapper o) {
      return myId.compareTo(o.myId);
    }
  }
}
