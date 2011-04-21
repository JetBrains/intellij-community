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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceParameter.AbstractInplaceIntroducer;
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.TitlePanel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 * Date: 3/18/11
 */
public class InplaceIntroduceConstantPopup {
  private final Project myProject;
  private final PsiClass myParentClass;
  private PsiExpression myExpr;
  private final PsiLocalVariable myLocalVariable;
  private final PsiExpression[] myOccurrences;
  private TypeSelectorManagerImpl myTypeSelectorManager;
  private PsiElement myAnchorElement;
  private int myAnchorIdx = -1;
  private PsiElement myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;
  private final OccurenceManager myOccurenceManager;

  private Editor myEditor;
  private String myConstantName;
  private List<RangeMarker> myOccurrenceMarkers;
  private final String myExprText;
  private final String myLocalName;
  private RangeMarker myExprMarker;

  private boolean myInitListeners = false;
  private JCheckBox myReplaceAllCb;
  private JCheckBox myAnnotateNonNls;
  private StateRestoringCheckBox myCbDeleteVariable;

  private JCheckBox myMoveToAnotherClassCb;

  private JComboBox myVisibilityCombo;

  private JPanel myWholePanel;

  public InplaceIntroduceConstantPopup(Project project,
                                       Editor editor,
                                       PsiClass parentClass,
                                       PsiExpression expr,
                                       PsiLocalVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager,
                                       PsiElement anchorElement,
                                       PsiElement anchorElementIfAll, OccurenceManager occurenceManager) {
    myProject = project;
    myEditor = editor;
    myParentClass = parentClass;
    myExpr = expr;
    myLocalVariable = localVariable;
    myOccurrences = occurrences;
    myTypeSelectorManager = typeSelectorManager;
    myAnchorElement = anchorElement;
    myAnchorElementIfAll = anchorElementIfAll;
    for (int i = 0, occurrencesLength = occurrences.length; i < occurrencesLength; i++) {
      PsiExpression occurrence = occurrences[i];
      PsiElement parent = occurrence.getParent();
      if (parent == myAnchorElement) {
        myAnchorIdx = i;
      }
      if (parent == myAnchorElementIfAll) {
        myAnchorIdxIfAll = i;
      }
    }
    myOccurenceManager = occurenceManager;

    myExprMarker = expr != null && expr.isPhysical() ? myEditor.getDocument().createRangeMarker(expr.getTextRange()) : null;
    myExprText = expr != null ? expr.getText() : null;
    myLocalName = localVariable != null ? localVariable.getName() : null;

    myWholePanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0 , 0, 0), 0, 0);

    final TitlePanel titlePanel = new TitlePanel();
    titlePanel.setBorder(null);
    titlePanel.setText(IntroduceConstantHandler.REFACTORING_NAME);
    gc.gridwidth = 2;
    myWholePanel.add(titlePanel, gc);

    gc.gridwidth = 1;
    gc.gridy = 1;
    myWholePanel.add(createLeftPanel(), gc);

    gc.gridx = 1;
    gc.insets.left = 6;
    myWholePanel.add(createRightPanel(), gc);

  }

  private JPanel createRightPanel() {
    final JPanel right = new JPanel(new GridBagLayout());
    final GridBagConstraints rgc = new GridBagConstraints(0, GridBagConstraints.RELATIVE,1,1,0,0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(1,0,0,0), 0, 0);
    myReplaceAllCb = new JCheckBox("Replace all occurrences");
    myReplaceAllCb.setMnemonic('a');
    myReplaceAllCb.setFocusable(false);
    myReplaceAllCb.setVisible(myOccurrences.length > 1);
    right.add(myReplaceAllCb, rgc);

    myCbDeleteVariable = new StateRestoringCheckBox("Delete variable declaration");
    myCbDeleteVariable.setMnemonic('d');
    myCbDeleteVariable.setFocusable(false);
    if (myLocalVariable != null) {
      if (myReplaceAllCb != null) {
        myReplaceAllCb.setEnabled(false);
        myReplaceAllCb.setSelected(true);
        myCbDeleteVariable.setSelected(true);
        myCbDeleteVariable.setEnabled(false);
      }
    } else {
      myCbDeleteVariable.setVisible(false);
    }
    right.add(myCbDeleteVariable, rgc);

    myAnnotateNonNls = new JCheckBox("Annotate field as @NonNls");
    myAnnotateNonNls.setMnemonic('f');
    myAnnotateNonNls.setFocusable(false);
    if ((myTypeSelectorManager.isSuggestedType("java.lang.String") || (myLocalVariable != null && AnnotationUtil
      .isAnnotated(myLocalVariable, AnnotationUtil.NON_NLS, false)))&&
        LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel().hasEnumKeywordAndAutoboxing() &&
        JavaPsiFacade.getInstance(myProject).findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myAnnotateNonNls.setSelected(component.isTrueValue(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY));
      myAnnotateNonNls.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          component.setValue(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY, Boolean.toString(myAnnotateNonNls.isSelected()));
        }
      });
    } else {
      myAnnotateNonNls.setVisible(false);
    }
    right.add(myAnnotateNonNls, rgc);
    return right;
  }

  private JPanel createLeftPanel() {
    final JPanel left = new JPanel(new GridBagLayout());
    myVisibilityCombo = createVisibilityCombo(left, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(6,5,0,0), 0, 0),
                                              myProject);
    myMoveToAnotherClassCb = new JCheckBox("Move to another class");
    myMoveToAnotherClassCb.setMnemonic('m');
    myMoveToAnotherClassCb.setFocusable(false);
    left.add(myMoveToAnotherClassCb, new GridBagConstraints(0, 1, 2, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    return left;
  }

  public static JComboBox createVisibilityCombo(final JPanel left,
                                                final GridBagConstraints lgc,
                                                final Project project) {

    final JLabel label = new JLabel("Visibility:");
    label.setDisplayedMnemonic('V');
    left.add(label, lgc);
    final JComboBox visibilityCombo = new JComboBox(new String[]{PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PRIVATE});
    visibilityCombo.setRenderer(new ListCellRendererWrapper<String>(visibilityCombo.getRenderer()) {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        if (value == null) return;
        setText(PsiBundle.visibilityPresentation(value));
      }
    });
    label.setLabelFor(visibilityCombo);
    visibilityCombo.setSelectedItem(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);

    appendActions(visibilityCombo, project);
    lgc.gridx++;
    lgc.insets.top = 2;
    lgc.insets.left = 0;
    left.add(visibilityCombo, lgc);
    return visibilityCombo;
  }

  public static void appendActions(final JComboBox visibilityCombo, final Project project) {
    final AnAction arrow = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (e.getInputEvent() instanceof KeyEvent) {
          final int code = ((KeyEvent)e.getInputEvent()).getKeyCode();
          final int delta = code == KeyEvent.VK_DOWN ? 1 : code == KeyEvent.VK_UP ? -1 : 0;
          if (delta == 0) return;
          final int size = visibilityCombo.getModel().getSize();
          int next = visibilityCombo.getSelectedIndex() + delta;
          if (next < 0 || next >= size) {
            if (!UISettings.getInstance().CYCLE_SCROLLING) {
              return;
            }
            next = (next + size) % size;
          }
          visibilityCombo.setSelectedIndex(next);
        }
      }
    };
    arrow.registerCustomShortcutSet(new CustomShortcutSet(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), null),
                                                          new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), null)), visibilityCombo);

    visibilityCombo.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      }
    });
  }

  public void performInplaceIntroduce() {
    startIntroduceTemplate(false, null);
  }

  private void startIntroduceTemplate(final boolean replaceAllOccurrences, @Nullable final PsiType fieldDefaultType) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        myTypeSelectorManager.setAllOccurences(replaceAllOccurrences);
        PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
        if (fieldDefaultType != null) {
          if (replaceAllOccurrences) {
            if (ArrayUtil.find(myTypeSelectorManager.getTypesForAll(), fieldDefaultType) != -1) {
              defaultType = fieldDefaultType;
            }
          }
          else if (ArrayUtil.find(myTypeSelectorManager.getTypesForOne(), fieldDefaultType) != -1) {
            defaultType = fieldDefaultType;
          }
        }
        final String propName = myLocalVariable != null ? JavaCodeStyleManager
          .getInstance(myProject).variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE) : null;
        final String[] names = IntroduceConstantDialog.createNameSuggestionGenerator(propName, myExpr, JavaCodeStyleManager.getInstance(myProject))
          .getSuggestedNameInfo(defaultType).names;
        final PsiField field = createFieldToStartTemplateOn(names, defaultType);
        if (field != null) {
          myEditor.getCaretModel().moveToOffset(field.getTextOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
          nameSuggestions.add(field.getName());
          nameSuggestions.addAll(Arrays.asList(names));
          final VariableInplaceRenamer renamer = new FieldInplaceIntroducer(field);
          renamer.performInplaceRename(false, nameSuggestions);
        }
      }
    }, IntroduceConstantHandler.REFACTORING_NAME, IntroduceConstantHandler.REFACTORING_NAME);
  }

  private PsiField createFieldToStartTemplateOn(final String[] names, final PsiType psiType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {
        PsiField field = elementFactory.createFieldFromText(psiType.getCanonicalText() + " " + (myConstantName != null ? myConstantName : names[0]) + " = " + myExprText + ";", myParentClass);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
        final String visibility = getSelectedVisibility();
        if (visibility != null) {
          PsiUtil.setModifierProperty(field, visibility, true);
        }
        field = BaseExpressionToFieldHandler.ConvertToFieldRunnable.appendField(myExpr, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, myParentClass, myParentClass, myAnchorElementIfAll, field);
        return field;
      }
    });
  }

  private String getSelectedVisibility() {
    return (String)myVisibilityCombo.getSelectedItem();
  }

  public List<RangeMarker> getOccurrenceMarkers() {
    if (myOccurrenceMarkers == null) {
      myOccurrenceMarkers = new ArrayList<RangeMarker>();
      for (PsiExpression occurrence : myOccurrences) {
        myOccurrenceMarkers.add(myEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
      }
    }
    return myOccurrenceMarkers;
  }

  private void updateCbDeleteVariable() {
    if (!myReplaceAllCb.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    }
    else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private class FieldInplaceIntroducer extends AbstractInplaceIntroducer {
    private RangeMarker myFieldRangeStart;


    private SmartTypePointer myDefaultParameterTypePointer;

    private SmartTypePointer myFieldTypePointer;

    public FieldInplaceIntroducer(PsiField field) {
      super(myProject, new TypeExpression(myProject, myReplaceAllCb.isSelected() ? myTypeSelectorManager.getTypesForAll() : myTypeSelectorManager.getTypesForOne()),
            myEditor, field, false,
            myTypeSelectorManager.getTypesForAll().length > 1,
            myExpr != null && myExpr.isPhysical() ? myEditor.getDocument().createRangeMarker(myExpr.getTextRange()) : null, InplaceIntroduceConstantPopup.this.getOccurrenceMarkers(),
            IntroduceConstantHandler.REFACTORING_NAME);

      myDefaultParameterTypePointer =
        SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(myTypeSelectorManager.getDefaultType());
      myFieldRangeStart = myEditor.getDocument().createRangeMarker(field.getTextRange());
    }

    @Override
    protected boolean isReplaceAllOccurrences() {
      return myReplaceAllCb.isSelected();
    }

    @Override
    protected PsiExpression getExpr() {
      return myExpr != null && myExpr.isValid() && myExpr.isPhysical() ? myExpr : null;
    }

    @Override
    protected PsiExpression[] getOccurrences() {
      return myOccurrences;
    }

    @Override
    protected List<RangeMarker> getOccurrenceMarkers() {
      return InplaceIntroduceConstantPopup.this.getOccurrenceMarkers();
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myParentClass;
    }

    @Override
    public RangeMarker getExprMarker() {
      return myExprMarker;
    }

    @Override
    protected void saveSettings(PsiVariable psiVariable) {
      TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultParameterTypePointer.getType());
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getSelectedVisibility();
    }

    @Override
    protected PsiVariable getVariable() {
      PsiElement element = myParentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
      if (element instanceof PsiWhiteSpace) {
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
      }
      return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
      if (success) {
        final BaseExpressionToFieldHandler.Settings settings =
          new BaseExpressionToFieldHandler.Settings(myConstantName,
                                                    isReplaceAllOccurrences(), true,
                                                    true,
                                                    BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                    getSelectedVisibility(), myLocalVariable,
                                                    myFieldTypePointer.getType(),
                                                    isDeleteVariable(),
                                                    myParentClass, isAnnotateNonNls(), false);
        final Runnable runnable = new Runnable() {
          public void run() {
            if (myLocalVariable != null) {
              final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
                new LocalToFieldHandler.IntroduceFieldRunnable(false, myLocalVariable, myParentClass, settings, true, myOccurrences);
              fieldRunnable.run();
            }
            else {
              final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
                new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                        myOccurrences, myOccurenceManager,
                                                                        myAnchorElementIfAll, myAnchorElement, myEditor, myParentClass);
              convertToFieldRunnable.run();
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
      super.moveOffsetAfter(success);
      if (myMoveToAnotherClassCb.isSelected()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            MoveMembersImpl.doMove(myProject, new PsiElement[]{myParentClass.findFieldByName(myConstantName, false)}, null, null);
          }
        });
      }
    }

    @Override
    protected JComponent getComponent() {
      if (!myInitListeners) {
        myInitListeners = true;
        final VisibilityListener visibilityListener = new VisibilityListener(myProject, IntroduceConstantHandler.REFACTORING_NAME, myEditor) {
          @Override
          protected String getVisibility() {
            return getSelectedVisibility();
          }
        };
        myVisibilityCombo.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            visibilityListener.stateChanged(null);
          }
        });
        myReplaceAllCb.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
            if (templateState != null) {
              templateState.gotoEnd(true);
              myTypeSelectorManager = new TypeSelectorManagerImpl(myProject, myDefaultParameterTypePointer.getType(), null, myExpr, myOccurrences);
              startIntroduceTemplate(isReplaceAllOccurrences(), myFieldTypePointer.getType());
            }
          }
        });

        myAnnotateNonNls.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            //todo it is unresolved; import here is not a good idea new FinalListener(myProject).perform(myAnnotateNonNls.isSelected(), "@NonNls");
          }
        });
      }
      return myWholePanel;
    }

    @Override
    public void finish() {
      super.finish();
      final PsiField psiField = (PsiField)getVariable();
      LOG.assertTrue(psiField != null);
      myFieldTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiField.getType());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myConstantName = psiField.getName();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PsiFile containingFile = myParentClass.getContainingFile();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
          final RangeMarker exprMarker = getExprMarker();
          if (exprMarker != null) {
            myExpr = restoreExpression(containingFile, psiField, elementFactory, exprMarker, myExprText);
            if (myExpr != null && myExpr.isPhysical()) {
              myExprMarker = myEditor.getDocument().createRangeMarker(myExpr.getTextRange());
            }
          }
          final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
          for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
            RangeMarker marker = occurrenceMarkers.get(i);
            if (getExprMarker() != null && marker.getStartOffset() == getExprMarker().getStartOffset()) {
              myOccurrences[i] = myExpr;
              continue;
            }
            final PsiExpression psiExpression = restoreExpression(containingFile, psiField, elementFactory, marker, myLocalVariable != null ? myLocalName : myExprText);
            if (psiExpression != null) {
              myOccurrences[i] = psiExpression;
            }
          }

          if (myAnchorIdxIfAll != -1 && myOccurrences[myAnchorIdxIfAll] != null) {
            myAnchorElementIfAll = myOccurrences[myAnchorIdxIfAll].getParent();
          }

          if (myAnchorIdx != -1 && myOccurrences[myAnchorIdx] != null) {
            myAnchorElement = myOccurrences[myAnchorIdx].getParent();
          }
          myOccurrenceMarkers = null;
          if (psiField.isValid()) {
            psiField.delete();
          }
        }
      });
    }
  }

  private boolean isAnnotateNonNls() {
    return myAnnotateNonNls.isSelected();
  }

  private boolean isDeleteVariable() {
    return myCbDeleteVariable.isSelected();
  }
}
