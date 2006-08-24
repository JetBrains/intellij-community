package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.*;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class IntentionManagerImpl extends IntentionManager {
  private List<IntentionAction> myActions = new ArrayList<IntentionAction>();
  private IntentionManagerSettings mySettings;

  public IntentionManagerImpl(IntentionManagerSettings intentionManagerSettings) {
    mySettings = intentionManagerSettings;

    addAction(new QuickFixAction());
    addAction(new PostIntentionsQuickFixAction());

    String[] CONTROL_FLOW_CATEGORY = new String[]{CodeInsightBundle.message("intentions.category.control.flow")};
    registerIntentionAndMetaData(new SplitIfAction(), CONTROL_FLOW_CATEGORY);
    registerIntentionAndMetaData(new InvertIfConditionAction(), CONTROL_FLOW_CATEGORY);
    registerIntentionAndMetaData(new RemoveRedundantElseAction(), CONTROL_FLOW_CATEGORY);

    String[] DECLARATION_CATEGORY = new String[]{CodeInsightBundle.message("intentions.category.declaration")};
    registerIntentionAndMetaData(new CreateFieldFromParameterAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new AssignFieldFromParameterAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new CreateLocalVarFromInstanceofAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new ImplementAbstractClassAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new ImplementAbstractMethodAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new SplitDeclarationAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new MoveInitializerToConstructorAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new MoveFieldAssignmentToInitializerAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new AddRuntimeExceptionToThrowsAction(), DECLARATION_CATEGORY);

    registerIntentionAndMetaData(new SimplifyBooleanExpressionAction(), CodeInsightBundle.message("intentions.category.boolean"));
    registerIntentionAndMetaData(new ConcatenationToMessageFormatAction(), CodeInsightBundle.message("intentions.category.i18n"));

    registerIntentionAndMetaData(new MakeTypeGeneric(), CodeInsightBundle.message("intentions.category.declaration"));
    registerIntentionAndMetaData(new AddOverrideAnnotationAction(), CodeInsightBundle.message("intentions.category.declaration"));

    registerIntentionAndMetaData(new AddOnDemandStaticImportAction(), CodeInsightBundle.message("intentions.category.imports"));
    registerIntentionAndMetaData(new AddSingleMemberStaticImportAction(), CodeInsightBundle.message("intentions.category.imports"));

    
    addAction(new EditFoldingOptionsAction());
  }

  public void registerIntentionAndMetaData(IntentionAction action, String... category) {
    registerIntentionAndMetaData(action, category, getDescriptionDirectoryName(action));
  }

  @NotNull
  private static String getDescriptionDirectoryName(final IntentionAction action) {
    final String fqn = action.getClass().getName();
    return fqn.substring(fqn.lastIndexOf('.') + 1);
  }

  public void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String[] category, @NotNull String descriptionDirectoryName) {
    addAction(action);
    mySettings.registerIntentionMetaData(action, category, descriptionDirectoryName);
  }

  public List<IntentionAction> getStandardIntentionOptions(final HighlightDisplayKey displayKey, final PsiElement context) {
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new EditInspectionToolsSettingsAction(displayKey));
    options.add(new AddNoInspectionCommentAction(displayKey, context));
    options.add(new AddNoInspectionDocTagAction(displayKey, context));
    options.add(new AddNoInspectionForClassAction(displayKey, context));
    options.add(new AddNoInspectionAllForClassAction(context));
    options.add(new AddSuppressWarningsAnnotationAction(displayKey, context));
    options.add(new AddSuppressWarningsAnnotationForClassAction(displayKey, context));
    options.add(new AddSuppressWarningsAnnotationForAllAction(context));
    options.add(new DisableInspectionToolAction(displayKey));
    return options;
  }

  public void initComponent() { }

  public void disposeComponent(){
  }

  @NotNull
  public String getComponentName(){
    return "IntentionManager";
  }

  public void projectOpened(){
  }

  public void projectClosed(){
  }

  public void addAction(IntentionAction action) {
    myActions.add(action);
  }

  public IntentionAction[] getIntentionActions() {
    return myActions.toArray(new IntentionAction[myActions.size()]);
  }

}
