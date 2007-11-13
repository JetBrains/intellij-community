package com.intellij.openapi.project.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StoresFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 *
 */
public class ProjectImpl extends ComponentManagerImpl implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  private ProjectManagerImpl myManager;

  private MyProjectManagerListener myProjectManagerListener;
  private boolean myDummy;

  private ArrayList<String> myConversionProblemsStorage = new ArrayList<String>();

  @NonNls private static final String PROJECT_LAYER = "project-components";

  public boolean myOptimiseTestLoadSpeed;
  @NonNls private static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";
  @NonNls private static final String DUMMY_PROJECT_NAME = "Dummy (Mock) Project";
  private boolean myDefault;
  private static final String DEPRECATED_MESSAGE = "Deprecated method usage: {0}.\n" +
           "This method will cease to exist in IDEA 7.0 final release.\n" +
           "Please contact plugin developers for plugin update.";

  private final Condition myDisposedCondition = new Condition() {
    public boolean value(final Object o) {
      return isDisposed();
    }
  };

  protected ProjectImpl(ProjectManagerImpl manager, String filePath, boolean isDefault, boolean isOptimiseTestLoadSpeed) {
    super(ApplicationManager.getApplication());

    myDefault = isDefault;

    getStateStore().setProjectFilePath(filePath);

    myOptimiseTestLoadSpeed = isOptimiseTestLoadSpeed;

    myManager = manager;
  }

  protected void boostrapPicoContainer() {
    Extensions.instantiateArea(PluginManager.AREA_IDEA_PROJECT, this, null);
    super.boostrapPicoContainer();
    final MutablePicoContainer picoContainer = getPicoContainer();

    picoContainer.registerComponentImplementation(ProjectPathMacroManager.class);
    picoContainer.registerComponent(new ComponentAdapter() {
      ComponentAdapter myDelegate;


      public ComponentAdapter getDelegate() {
        if (myDelegate == null) {
          Class storeClass = StoresFactory.getProjectStoreClass(myDefault);

          myDelegate = new CachingComponentAdapter(
            new ConstructorInjectionComponentAdapter(storeClass, storeClass, null, true));
        }

        return myDelegate;
      }

      public Object getComponentKey() {
        return IComponentStore.class;
      }

      public Class getComponentImplementation() {
        return getDelegate().getComponentImplementation();
      }

      public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
        return getDelegate().getComponentInstance(container);
      }

      public void verify(final PicoContainer container) throws PicoIntrospectionException {
        getDelegate().verify(container);
      }

      public void accept(final PicoVisitor visitor) {
        visitor.visitComponentAdapter(this);
        getDelegate().accept(visitor);
      }
    });

  }

  @NotNull
  public IProjectStore getStateStore() {
    return (IProjectStore)super.getStateStore();
  }

  public boolean isSavePathsRelative() {
    return getStateStore().isSavePathsRelative();
  }

  public void setSavePathsRelative(boolean b) {
    getStateStore().setSavePathsRelative(b);
  }

  public boolean isDummy() {
    return myDummy;
  }

  public boolean isOpen() {
    return ProjectManagerEx.getInstanceEx().isProjectOpened(this);
  }

  public Condition getDisposed() {
    return myDisposedCondition;
  }

  public boolean isInitialized() {
    return isOpen() && !isDisposed() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  public void setDummy(boolean isDummy) {
    myDummy = isDummy;
  }

  public ArrayList<String> getConversionProblemsStorage() {
    return myConversionProblemsStorage;
  }

  public void loadProjectComponents() {
    boolean realProject = !isDummy();
    loadComponentsConfiguration(PROJECT_LAYER, realProject);

    if (realProject) {
      final Application app = ApplicationManager.getApplication();
      final IdeaPluginDescriptor[] plugins = app.getPlugins();
      for (IdeaPluginDescriptor plugin : plugins) {
        if (PluginManager.shouldSkipPlugin(plugin)) continue;
        loadComponentsConfiguration(plugin.getProjectComponents(), plugin, true);
      }
    }
  }

  @NotNull
  public String getProjectFilePath() {
    LOG.warn(
      MessageFormat.format(DEPRECATED_MESSAGE, "ProjectImpl.getProjectFilePath()"),
      new Throwable());
    return getStateStore().getProjectFilePath();
  }

  /**
   * @deprecated
   */
  @Nullable
  public VirtualFile getProjectFile() {
    LOG.warn(
      MessageFormat.format(DEPRECATED_MESSAGE, "ProjectImpl.getProjectFile()"),
      new Throwable());
    return getStateStore().getProjectFile();
  }

  @Nullable
  public VirtualFile getBaseDir() {
    return getStateStore().getProjectBaseDir();
  }

  @NotNull
  public String getName() {
    if (isDefault()) return TEMPLATE_PROJECT_NAME;
    if (isDummy()) return DUMMY_PROJECT_NAME;

    return getStateStore().getProjectName();
  }

  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return getStateStore().getPresentableUrl();
  }

  @NotNull
  @NonNls
  public String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) str = getName();

    return getName() + Integer.toHexString(str.hashCode());
  }

  @NotNull
  @NonNls
  public String getLocation() {
    return getStateStore().getLocation();
  }

  /**
   * @deprecated
   */
  @Nullable
  public VirtualFile getWorkspaceFile() {
    LOG.warn(
      MessageFormat.format(DEPRECATED_MESSAGE, "ProjectImpl.getWorkspaceFile()"),
      new Throwable());
    return getStateStore().getWorkspaceFile();
  }

  public boolean isOptimiseTestLoadSpeed() {
    return myOptimiseTestLoadSpeed;
  }

  public void setOptimiseTestLoadSpeed(final boolean optimiseTestLoadSpeed) {
    myOptimiseTestLoadSpeed = optimiseTestLoadSpeed;
  }


  public void init() {
    super.init();
    getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).projectComponentsInitialized(this);
    myProjectManagerListener = new MyProjectManagerListener();
    myManager.addProjectManagerListener(this, myProjectManagerListener);
  }

  public void save() {
    if (ApplicationManagerEx.getApplicationEx().isDoNotSave()) return; //no need to save

    try {
      doSave();
    }
    catch (IComponentStore.SaveCancelledException e) {
      LOG.info(e);
    }
    catch (IOException e) {
      LOG.info(e);
      MessagesEx.error(this, ProjectBundle.message("project.save.error", e.getMessage())).showLater();
    }
  }

  public synchronized void dispose() {
    LOG.assertTrue(!isDisposed());
    if (myProjectManagerListener != null) {
      myManager.removeProjectManagerListener(this, myProjectManagerListener);
    }

    disposeComponents();
    Extensions.disposeArea(this);
    myManager = null;
    myProjectManagerListener = null;
    super.dispose();
  }

  private void projectOpened() {
    final ProjectComponent[] components = getComponents(ProjectComponent.class);
    for (ProjectComponent component : components) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }
  

  private void projectClosed() {
    List<ProjectComponent> components = new ArrayList<ProjectComponent>(Arrays.asList(getComponents(ProjectComponent.class)));
    Collections.reverse(components);
    for (ProjectComponent component : components) {
      try {
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getArea(this).getExtensionPoint(extensionPointName).getExtensions();
  }

  private class MyProjectManagerListener implements ProjectManagerListener {
    public void projectOpened(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectOpened();
    }

    public void projectClosed(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectClosed();
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosing(Project project) {
    }
  }

  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getArea(this).getPicoContainer();
  }

  public boolean isDefault() {
    return myDefault;
  }
}
