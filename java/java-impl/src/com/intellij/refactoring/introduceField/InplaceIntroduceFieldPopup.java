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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceParameter.AbstractInplaceIntroducer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 * Date: 3/15/11
 */
public class InplaceIntroduceFieldPopup {

  private PsiLocalVariable myLocalVariable;
  private PsiClass myParentClass;
  private boolean myStatic;
  private PsiExpression[] myOccurrences;
  private PsiExpression myInitializerExpression;
  private final Editor myEditor;
  private Project myProject;

  private TypeSelectorManagerImpl myTypeSelectorManager;
  private final PsiElement myAnchorElement;
  private int myAnchorIdx = -1;
  private final PsiElement myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;
  private final OccurenceManager myOccurenceManager;
  private List<RangeMarker> myOccurrenceMarkers;

  private final IntroduceFieldCentralPanel myIntroduceFieldPanel;

  private JPanel myWholePanel;
  private String myExprText;
  private String myLocalName;
  private RangeMarker myExprMarker;
  private String myFieldName;

  private boolean myInitListeners = false;
  static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

  public InplaceIntroduceFieldPopup(PsiLocalVariable localVariable,
                                    PsiClass parentClass,
                                    boolean aStatic,
                                    boolean currentMethodConstructor, PsiExpression[] occurrences,
                                    PsiExpression initializerExpression,
                                    TypeSelectorManagerImpl typeSelectorManager,
                                    Editor editor,
                                    final boolean allowInitInMethod,
                                    boolean allowInitInMethodIfAll, final PsiElement anchorElement,
                                    final PsiElement anchorElementIfAll,
                                    final OccurenceManager occurenceManager) {
    myLocalVariable = localVariable;
    myParentClass = parentClass;
    myStatic = aStatic;
    myOccurrences = occurrences;
    myInitializerExpression = initializerExpression;
    myExprText = myInitializerExpression != null ? myInitializerExpression.getText() : null;
    myLocalName = localVariable != null ? localVariable.getName() : null;
    myExprMarker = myInitializerExpression != null && myInitializerExpression.isPhysical() ? editor.getDocument().createRangeMarker(myInitializerExpression.getTextRange()) : null;
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
    myProject = myLocalVariable != null ? myLocalVariable.getProject() : myInitializerExpression.getProject();
    myEditor = editor;

    myIntroduceFieldPanel =
      new IntroduceFieldPopupPanel(parentClass, initializerExpression, localVariable, currentMethodConstructor, localVariable != null, aStatic,
                               myOccurrences, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(null);

    GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             new Insets(0,0,0,0), 0, 0);


    gc.gridy++;
    gc.insets.top = 5;

    myWholePanel.add(myIntroduceFieldPanel.createCenterPanel(), gc);
    myIntroduceFieldPanel.initializeControls(initializerExpression, ourLastInitializerPlace);


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

  public void startTemplate() {
    startTemplate(false, null);
  }

  public void startTemplate(final boolean replaceAllOccurrences, @Nullable final PsiType fieldDefaultType) {
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

        final SuggestedNameInfo suggestedNameInfo =
          IntroduceFieldDialog.createGenerator(myStatic, myLocalVariable, myInitializerExpression, myLocalVariable != null)
            .getSuggestedNameInfo(defaultType);

        final PsiField field = createFieldToStartTemplateOn(suggestedNameInfo.names, defaultType);
        if (field != null) {
          myEditor.getCaretModel().moveToOffset(field.getTextOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
          nameSuggestions.add(field.getName());
          nameSuggestions.addAll(Arrays.asList(suggestedNameInfo.names));
          final VariableInplaceRenamer renamer = new FieldInplaceIntroducer(field);
          renamer.performInplaceRename(false, nameSuggestions);
        }
      }
    }, IntroduceFieldHandler.REFACTORING_NAME, IntroduceFieldHandler.REFACTORING_NAME);
  }

  private PsiField createFieldToStartTemplateOn(final String[] names,
                                                final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {
        final Ref<PsiField> ref = new Ref<PsiField>();
        PostprocessReformattingAspect.getInstance(myProject).postponeFormattingInside(new Runnable() {
          @Override
          public void run() {
            PsiField field = elementFactory.createField(myFieldName != null ? myFieldName : names[0], defaultType);
            field = (PsiField)myParentClass.add(field);
            PsiUtil.setModifierProperty(field, PsiModifier.FINAL, myIntroduceFieldPanel.isDeclareFinal());
            final String visibility = myIntroduceFieldPanel.getFieldVisibility();
            if (visibility != null) {
              PsiUtil.setModifierProperty(field, visibility, true);
            }
            ref.set(field);
          }
        });

        return ref.get();
      }
    });
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    myIntroduceFieldPanel.setReplaceAllOccurrences(replaceAllOccurrences);
  }

  public void setCreateFinal(boolean createFinal) {
    myIntroduceFieldPanel.setCreateFinal(createFinal);
  }

  public void setInitializeInFieldDeclaration() {
    myIntroduceFieldPanel.setInitializeInFieldDeclaration();
  }

  public void setVisibility(String visibility) {
    myIntroduceFieldPanel.setVisibility(visibility);
  }

  public static void setInitializationPlace(BaseExpressionToFieldHandler.InitializationPlace place) {
    ourLastInitializerPlace = place;
  }

  class FieldInplaceIntroducer extends AbstractInplaceIntroducer {

    private RangeMarker myFieldRangeStart;


    private SmartTypePointer myDefaultParameterTypePointer;

    private SmartTypePointer myFieldTypePointer;

    public FieldInplaceIntroducer(PsiVariable psiVariable) {
      super(myProject, new TypeExpression(myProject, myIntroduceFieldPanel.isReplaceAllOccurrences() ? myTypeSelectorManager.getTypesForAll() : myTypeSelectorManager.getTypesForOne()),
            myEditor, psiVariable, false,
            myTypeSelectorManager.getTypesForAll().length > 1,
            myInitializerExpression != null && myInitializerExpression.isPhysical() ? myEditor.getDocument().createRangeMarker(myInitializerExpression.getTextRange()) : null, InplaceIntroduceFieldPopup.this.getOccurrenceMarkers(),
            IntroduceFieldHandler.REFACTORING_NAME, IntroduceFieldHandler.REFACTORING_NAME);
      myDefaultParameterTypePointer =
        SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(myTypeSelectorManager.getDefaultType());
      myFieldRangeStart = myEditor.getDocument().createRangeMarker(psiVariable.getTextRange());
    }

    @Override
    protected boolean isReplaceAllOccurrences() {
      return myIntroduceFieldPanel.isReplaceAllOccurrences();
    }

    @Override
    protected PsiExpression getExpr() {
      return myInitializerExpression != null && myInitializerExpression.isValid() && myInitializerExpression.isPhysical() ? myInitializerExpression : null;
    }

    @Override
    protected PsiExpression[] getOccurrences() {
      return myOccurrences;
    }

    @Override
    protected List<RangeMarker> getOccurrenceMarkers() {
      return InplaceIntroduceFieldPopup.this.getOccurrenceMarkers();
    }

    @Override
    public RangeMarker getExprMarker() {
      return myExprMarker;
    }

    @Override
    protected void saveSettings(PsiVariable psiVariable) {
      TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultParameterTypePointer.getType());
      JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY = myIntroduceFieldPanel.getFieldVisibility();
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myParentClass;
    }

    @Override
    protected JComponent getComponent() {
      if (!myInitListeners) {
        myInitListeners = true;
        myIntroduceFieldPanel.addVisibilityListener(new VisibilityListener(myProject, IntroduceFieldHandler.REFACTORING_NAME, myEditor){
          @Override
          protected String getVisibility() {
            return myIntroduceFieldPanel.getFieldVisibility();
          }
        });
        final FinalListener finalListener = new FinalListener(myProject, IntroduceFieldHandler.REFACTORING_NAME);
        myIntroduceFieldPanel.addFinalListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            finalListener.perform(myIntroduceFieldPanel.isDeclareFinal());
          }
        });
        myIntroduceFieldPanel.addOccurrenceListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            final Runnable restartTemplate = new Runnable() {
              @Override
              public void run() {
                final TemplateState templateState =
                  TemplateManagerImpl.getTemplateState(myEditor);
                if (templateState != null) {
                  templateState.gotoEnd(true);
                  myTypeSelectorManager = new TypeSelectorManagerImpl(myProject, myDefaultParameterTypePointer.getType(), null, myInitializerExpression, myOccurrences);
                  startTemplate(myIntroduceFieldPanel.isReplaceAllOccurrences(), myFieldTypePointer.getType());
                }
              }
            };
            CommandProcessor.getInstance().executeCommand(myProject, restartTemplate,
                                                          IntroduceFieldHandler.REFACTORING_NAME,
                                                          IntroduceFieldHandler.REFACTORING_NAME);
          }
        });
      }

      return myWholePanel;
    }

    @Override
    protected PsiVariable getVariable() {
      PsiElement element = myParentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
      if (element instanceof PsiWhiteSpace) {
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
      }
      if (element instanceof PsiField) return (PsiVariable)element;
      final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
      if (field != null) return field;
      element = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
      if (success) {
        if (myLocalVariable == null && myInitializerExpression == null || myFieldName == null) {
          super.moveOffsetAfter(false);
          return;
        }
        ourLastInitializerPlace = myIntroduceFieldPanel.getInitializerPlace();
        myIntroduceFieldPanel.saveFinalState();
        final BaseExpressionToFieldHandler.Settings settings =
          new BaseExpressionToFieldHandler.Settings(myFieldName, myIntroduceFieldPanel.isReplaceAllOccurrences(), myStatic,
                                                    myIntroduceFieldPanel.isDeclareFinal(),
                                                    myIntroduceFieldPanel.getInitializerPlace(),
                                                    myIntroduceFieldPanel.getFieldVisibility(), myLocalVariable,
                                                    myFieldTypePointer.getType(),
                                                    myIntroduceFieldPanel.isDeleteVariable(),
                                                    myParentClass, false, false);
        final Runnable runnable = new Runnable() {
          public void run() {
            if (myLocalVariable != null) {
              final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
                new LocalToFieldHandler.IntroduceFieldRunnable(false, myLocalVariable, myParentClass, settings, myStatic, myOccurrences);
              fieldRunnable.run();
            }
            else {
              final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
                new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myInitializerExpression, settings, settings.getForcedType(),
                                                                        myOccurrences, myOccurenceManager,
                                                                        myAnchorIdxIfAll != -1 && myOccurrences[myAnchorIdxIfAll] != null ? myOccurrences[myAnchorIdxIfAll].getParent() : myAnchorElementIfAll,
                                                                        myAnchorIdx != -1 && myOccurrences[myAnchorIdx] != null ? myOccurrences[myAnchorIdx].getParent() : myAnchorElement, myEditor,
                                                                        myParentClass);
              convertToFieldRunnable.run();
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
      super.moveOffsetAfter(success);
    }

    @Override
    public void finish() {
      super.finish();
      final PsiField psiField = (PsiField)getVariable();
      if (psiField == null) {
        return;
      }
      myFieldTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiField.getType());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myFieldName = psiField.getName();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PsiFile containingFile = myParentClass.getContainingFile();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
          if (getExprMarker() != null) {
            myInitializerExpression = restoreExpression(containingFile, psiField, elementFactory, getExprMarker(), myExprText);
            if (myInitializerExpression != null) {
              myExprMarker = myEditor.getDocument().createRangeMarker(myInitializerExpression.getTextRange());
            }
          }
          final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
          for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
            RangeMarker marker = occurrenceMarkers.get(i);
            if (getExprMarker() != null && marker.getStartOffset() == getExprMarker().getStartOffset()) {
              myOccurrences[i] = myInitializerExpression;
              continue;
            }
            final PsiExpression psiExpression =
              restoreExpression(containingFile, psiField, elementFactory, marker, myLocalVariable != null ? myLocalName : myExprText);
            if (psiExpression != null) {
              myOccurrences[i] = psiExpression;
            }
          }
          myOccurrenceMarkers = null;
          if (psiField.isValid()) {
            psiField.delete();
          }
        }
      });
    }
  }
}
