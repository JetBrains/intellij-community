package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Comparator;

abstract class BaseRunConfigurationAction extends AnAction {
  protected BaseRunConfigurationAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final ConfigurationContext context = new ConfigurationContext(dataContext);
    final RunnerAndConfigurationSettingsImpl existing = context.findExisting();
    if (existing == null) {
      final List<RuntimeConfigurationProducer> producers = PreferedProducerFind.findPreferedProducers(context.getLocation(), context);
      if (producers == null || producers.size() == 0) return;
      final RuntimeConfigurationProducer first = producers.get(0);
      for (Iterator<RuntimeConfigurationProducer> it = producers.iterator(); it.hasNext();) {
        RuntimeConfigurationProducer producer = it.next();
        if (RuntimeConfigurationProducer.COMPARATOR.compare(producer, first) >= 0) {
          it.remove();
        }
      }
      if (producers.size() > 1) {
        final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
        Collections.sort(producers, new Comparator<RuntimeConfigurationProducer>() {
          public int compare(final RuntimeConfigurationProducer p1, final RuntimeConfigurationProducer p2) {
            return p1.getConfigurationType().getDisplayName().compareTo(p2.getConfigurationType().getDisplayName());
          }
        });
        final ListPopup popup =
          JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<RuntimeConfigurationProducer>(ExecutionBundle.message("configuration.action.chooser.title"), producers) {
            @NotNull
            public String getTextFor(final RuntimeConfigurationProducer producer) {
              return producer.getConfigurationType().getDisplayName();
            }

            public Icon getIconFor(final RuntimeConfigurationProducer producer) {
              return producer.getConfigurationType().getIcon();
            }

            public PopupStep onChosen(final RuntimeConfigurationProducer producer, final boolean finalChoice) {
              final RunnerAndConfigurationSettings configuration = context.getConfiguration(producer);
              if (configuration != null) {
                perform(context);
              }
              return PopupStep.FINAL_CHOICE;
            }
          });
        if (editor != null) {
          popup.showInBestPositionFor(editor);
        } else {
          popup.showInBestPositionFor(dataContext);
        }
        return;
      }
    }
    final RunnerAndConfigurationSettingsImpl configuration = existing != null ? existing : context.getConfiguration();
    if (configuration == null) return;
    perform(context);
  }

  protected abstract void perform(ConfigurationContext context);

  public void update(final AnActionEvent event){
    final ConfigurationContext context = new ConfigurationContext(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    final RunnerAndConfigurationSettings configuration = context.getConfiguration();
    if (configuration == null){
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else{
      presentation.setEnabled(true);
      presentation.setVisible(true);
      final String name = suggestRunActionName((LocatableConfiguration)configuration.getConfiguration());
      updatePresentation(presentation, " " + name, context);
    }
  }

  public static String suggestRunActionName(final LocatableConfiguration configuration) {
    if (!configuration.isGeneratedName()) {
      return "\"" + ExecutionUtil.shortenName(configuration.getName(), 0) + "\"";
    } else return "\"" + configuration.suggestedName() + "\"";
  }

  protected abstract void updatePresentation(Presentation presentation, String actionText, ConfigurationContext context);

}
