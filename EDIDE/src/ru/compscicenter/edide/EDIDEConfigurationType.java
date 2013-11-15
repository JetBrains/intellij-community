package ru.compscicenter.edide;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EDIDEConfigurationType implements ConfigurationType {
    public static final String ID = "mytests";

    private final EDIDEConfigurationFactory myFactory = new EDIDEConfigurationFactory(this);

    public String getDisplayName(){
        return "My Python Unit Test configuration";
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

    private static class EDIDEConfigurationFactory extends ConfigurationFactory {
        protected EDIDEConfigurationFactory(ConfigurationType configurationType) {
            super(configurationType);
        }

        @Override
        public RunConfiguration createTemplateConfiguration(Project project) {
            return new EDIDEUnitTestRunConfiguration(project, this);
        }

        @Override
        public String getName() {
            return "Python test";
        }
    }

    public ConfigurationFactory[] getConfigurationFactories(){
        return new ConfigurationFactory[]{myFactory};
    }
}
