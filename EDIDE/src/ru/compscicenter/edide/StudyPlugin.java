package ru.compscicenter.edide;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Created by liana on 30.01.14.
 */
public class StudyPlugin implements ApplicationComponent {
    private final Application myApp;
    public StudyPlugin(final Application app) {
        myApp = app;
    }
    @Override
    public void initComponent() {

        EditorFactory.getInstance().addEditorFactoryListener(new StudyEditorFactoryListener(),myApp);
    }

    @Override
    public void disposeComponent() {

    }

    @NotNull
    @Override
    public String getComponentName() {
        return "Educational plugin";
    }
}
