package ru.compscicenter.edide;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyConfigurationType implements ConfigurationType {
    public static final String ID = "studytests";

    private final StudyConfigurationFactory myFactory = new StudyConfigurationFactory(this);

    public String getDisplayName(){
        return "Study unittest configuration";
    }

    public String getConfigurationTypeDescription() {
        return "Run configuration for our plugin.";
    }

    public Icon getIcon() {
        return PythonIcons.Python.PythonTests;
    }

    @NonNls
    @NotNull
    public String getId() {
        return ID;
    }

    private static class StudyConfigurationFactory extends ConfigurationFactory {
        protected StudyConfigurationFactory(ConfigurationType configurationType) {
            super(configurationType);
        }

        @Override
        public RunConfiguration createTemplateConfiguration(Project project) {
            return new StudyUnitTestRunConfiguration(project, this);
        }

        @Override
        public String getName() {
            return "Study test";
        }
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories(){
        return new ConfigurationFactory[]{myFactory};
    }
}
