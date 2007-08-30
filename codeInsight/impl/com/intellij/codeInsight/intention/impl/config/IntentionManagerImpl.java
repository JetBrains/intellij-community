package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.actions.*;
import com.intellij.codeInsight.daemon.impl.quickfix.AddRuntimeExceptionToThrowsAction;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalVarFromInstanceofAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveRedundantElseAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.*;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsInSuppressedPlaceIntention;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class IntentionManagerImpl extends IntentionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl");

  private List<IntentionAction> myActions = new ArrayList<IntentionAction>();
  private IntentionManagerSettings mySettings;

  public IntentionManagerImpl(IntentionManagerSettings intentionManagerSettings) {
    mySettings = intentionManagerSettings;

    addAction(new QuickFixAction());
    //addAction(new PostIntentionsQuickFixAction());

    String[] CONTROL_FLOW_CATEGORY = new String[]{CodeInsightBundle.message("intentions.category.control.flow")};
    registerIntentionAndMetaData(new SplitIfAction(), CONTROL_FLOW_CATEGORY);
    registerIntentionAndMetaData(new InvertIfConditionAction(), CONTROL_FLOW_CATEGORY);
    registerIntentionAndMetaData(new RemoveRedundantElseAction(), CONTROL_FLOW_CATEGORY);
    registerIntentionAndMetaData(new AddNotNullAnnotationFix(), CONTROL_FLOW_CATEGORY, "AddAnnotationFix");
    registerIntentionAndMetaData(new AddNullableAnnotationFix(), CONTROL_FLOW_CATEGORY, "AddAnnotationFix");
    registerIntentionAndMetaData(new DeannotateIntentionAction(), CONTROL_FLOW_CATEGORY);

    String[] DECLARATION_CATEGORY = new String[]{CodeInsightBundle.message("intentions.category.declaration")};
    registerIntentionAndMetaData(new CreateFieldFromParameterAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new AssignFieldFromParameterAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new CreateLocalVarFromInstanceofAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new CreateSubclassAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new ImplementAbstractMethodAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new SplitDeclarationAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new MoveInitializerToConstructorAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new MoveFieldAssignmentToInitializerAction(), DECLARATION_CATEGORY);
    registerIntentionAndMetaData(new AddRuntimeExceptionToThrowsAction(), DECLARATION_CATEGORY);

    registerIntentionAndMetaData(new SimplifyBooleanExpressionAction(), CodeInsightBundle.message("intentions.category.boolean"));
    registerIntentionAndMetaData(new ConcatenationToMessageFormatAction(), CodeInsightBundle.message("intentions.category.i18n"));

    registerIntentionAndMetaData(new MakeTypeGenericAction(), CodeInsightBundle.message("intentions.category.declaration"));
    registerIntentionAndMetaData(new AddOverrideAnnotationAction(), CodeInsightBundle.message("intentions.category.declaration"));

    registerIntentionAndMetaData(new AddOnDemandStaticImportAction(), CodeInsightBundle.message("intentions.category.imports"));
    registerIntentionAndMetaData(new AddSingleMemberStaticImportAction(), CodeInsightBundle.message("intentions.category.imports"));

    
    addAction(new EditFoldingOptionsAction());
    addAction(new EditInspectionToolsSettingsInSuppressedPlaceIntention());

    final ExtensionPoint<IntentionActionBean> point = Extensions.getArea(null).getExtensionPoint(EP_INTENTION_ACTIONS);

    point.addExtensionPointListener(new ExtensionPointListener<IntentionActionBean>() {
      public void extensionAdded(final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
        registerIntentionFromBean(extension, pluginDescriptor);
      }

      public void extensionRemoved(final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
      }
    });
  }

  private void registerIntentionFromBean(final IntentionActionBean extension, final PluginDescriptor pluginDescriptor) {
    ClassLoader classLoader = pluginDescriptor != null ? pluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
    try {
      final Class<?> aClass = Class.forName(extension.className, true, classLoader);
      final String descriptionDirectoryName = extension.getDescriptionDirectoryName();
      if (descriptionDirectoryName != null) {
        registerIntentionAndMetaData((IntentionAction)aClass.newInstance(), extension.className, descriptionDirectoryName);
      }
      else {
        registerIntentionAndMetaData((IntentionAction)aClass.newInstance(), extension.getCategories());
      }
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
  }

  public void registerIntentionAndMetaData(IntentionAction action, String... category) {
    registerIntentionAndMetaData(action, category, getDescriptionDirectoryName(action));
  }

  @NotNull
  private static String getDescriptionDirectoryName(final IntentionAction action) {
    final String fqn = action.getClass().getName();
    return fqn.substring(fqn.lastIndexOf('.') + 1);
  }

  public void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String[] category, @NotNull @NonNls String descriptionDirectoryName) {
    addAction(action);
    mySettings.registerIntentionMetaData(action, category, descriptionDirectoryName);
  }

  public List<IntentionAction> getStandardIntentionOptions(final HighlightDisplayKey displayKey, final PsiElement context) {
    List<IntentionAction> options = new ArrayList<IntentionAction>(9);
    options.add(new EditInspectionToolsSettingsAction(displayKey));
    options.add(new AddNoInspectionCommentFix(displayKey, context));
    options.add(new AddNoInspectionDocTagFix(displayKey, context));
    options.add(new AddNoInspectionForClassFix(displayKey, context));
    options.add(new AddNoInspectionAllForClassFix(context));
    options.add(new AddSuppressWarningsAnnotationFix(displayKey, context));
    options.add(new AddSuppressWarningsAnnotationForClassFix(displayKey, context));
    options.add(new AddSuppressWarningsAnnotationForAllFix(context));
    options.add(new DisableInspectionToolAction(displayKey));
    return options;
  }

  public void addAction(IntentionAction action) {
    myActions.add(action);
  }

  public IntentionAction[] getIntentionActions() {
    return myActions.toArray(new IntentionAction[myActions.size()]);
  }

}
