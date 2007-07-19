package com.intellij.execution.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Date: 28-Mar-2006
 */
@State(
  name = "ProjectRunConfigurationManager",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/runConfigurations/", scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = ProjectRunConfigurationManager.RunConfigurationStateSplitter.class)
    }
)
public class ProjectRunConfigurationManager implements ProjectComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ProjectRunConfigurationManager");

  private RunManagerImpl myManager;
  private List<Element> myUnloadedElements = null;

  public ProjectRunConfigurationManager(final RunManagerImpl manager) {
    myManager = manager;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "ProjectRunConfigurationManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public Element getState() {
     try {
       final Element e = new Element("state");
       writeExternal(e);
       return e;
     }
     catch (WriteExternalException e1) {
       LOG.error(e1);
       return null;
     }
   }

   public void loadState(Element state) {
     try {
       readExternal(state);
     }
     catch (InvalidDataException e) {
       LOG.error(e);
     }
   }

  public void readExternal(Element element) throws InvalidDataException {
    myUnloadedElements = null;
    myManager.clear();

    final List children = element.getChildren();
    for (final Object child : children) {
      if (!myManager.loadConfiguration((Element)child, true) && Comparing.strEqual(element.getName(), RunManagerImpl.CONFIGURATION)) {
        if (myUnloadedElements == null) myUnloadedElements = new ArrayList<Element>(2);
        myUnloadedElements.add(element);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Collection<RunnerAndConfigurationSettingsImpl> configurations = myManager.getStableConfigurations().values();
    for (RunnerAndConfigurationSettingsImpl configuration : configurations) {
      if (myManager.isConfigurationShared(configuration)){
        myManager.addConfigurationElement(element, configuration);
      }
    }
    if (myUnloadedElements != null) {
      for (Element unloadedElement : myUnloadedElements) {
        element.addContent((Element)unloadedElement.clone());
      }
    }
  }

  public static class RunConfigurationStateSplitter implements StateSplitter {
    public List<Pair<Element, String>> splitState(Element e) {
      final UniqueNameGenerator generator = new UniqueNameGenerator();

      List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

      final List list = e.getChildren();
      for (final Object o : list) {
        Element library = (Element)o;
        final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(library.getAttributeValue(RunManagerImpl.NAME_ATTR))) + ".xml";
        result.add(new Pair<Element, String>(library, name));
      }

      return result;
    }

    public void mergeStatesInto(Element target, Element[] elements) {
      for (Element e : elements) {
        target.addContent(e);
      }
    }
  }
}
