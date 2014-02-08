package ru.compscicenter.edide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.EditorFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by liana on 30.01.14.
 */


@State(
    name = "StudySettings",
    storages = {
        @Storage(
            id = "main",
            file = "$APP_CONFIG$/study_settings.xml"
        )}
)

public class StudyPlugin implements ApplicationComponent, PersistentStateComponent<Element> {
    public static final String CURRENT_TASK = "currentTask";
    private final Application myApp;

    public StudyPlugin(final Application app) {
        myApp = app;
    }

    @Override
    public void initComponent() {
        EditorFactory.getInstance().addEditorFactoryListener(new StudyEditorFactoryListener(), myApp);
    }

    @Override
    public void disposeComponent() {

    }

    @NotNull
    @Override
    public String getComponentName() {
        return "Educational plugin";
    }

    @Nullable
    @Override
    public Element getState() {
        Element plugin = new Element("studyPlugin");
        Element curTask =  new Element(CURRENT_TASK);
        curTask.setText(String.valueOf(TaskManager.getInstance().getCurrentTask()));
        plugin.addContent(curTask);
        return plugin;
    }

    @Override
    public void loadState(Element state) {
        int curTask = Integer.parseInt(state.getChild(CURRENT_TASK).getText());
        TaskManager.getInstance().setCurrentTask(curTask);
    }
}
