package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ResourceUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author max
 */
public class InspectionToolRegistrar implements ApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private ArrayList<Class> myInspectionTools;
  private ArrayList<Class> myLocalInspectionTools;
  private ArrayList<Class> myGlobalInspectionTools;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private static HashMap<String, ArrayList<String>> myWords2InspectionToolNameMap = null;

  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  public void ensureInitialized() {
    if (myInspectionTools == null) {
      Set<InspectionToolProvider> providers = new THashSet<InspectionToolProvider>();
      providers.addAll(Arrays.asList(ApplicationManager.getApplication().getComponents(InspectionToolProvider.class)));
      providers.addAll(Arrays.asList(Extensions.getExtensions(InspectionToolProvider.EXTENSION_POINT_NAME)));
      registerTools(providers.toArray(new InspectionToolProvider[providers.size()]));
    }
  }

  public void registerTools(final InspectionToolProvider[] providers) {
    myInspectionTools = new ArrayList<Class>();
    myLocalInspectionTools = new ArrayList<Class>();
    myGlobalInspectionTools = new ArrayList<Class>();
    for (InspectionToolProvider provider : providers) {
      Class[] classes = provider.getInspectionClasses();
      for (Class aClass : classes) {
        if (LocalInspectionTool.class.isAssignableFrom(aClass)) {
          registerLocalInspection(aClass);
        } else if (GlobalInspectionTool.class.isAssignableFrom(aClass)){
          registerGlobalInspection(aClass);
        } else {
          registerInspectionTool(aClass);
        }
      }
    }
  }

  public static InspectionToolRegistrar getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionToolRegistrar.class);
  }

  @NotNull
  public String getComponentName() {
    return "InspectionToolRegistrar";
  }

  public void readExternal(Element element) throws InvalidDataException {
  }

  public void writeExternal(Element element) throws WriteExternalException {
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  private void registerLocalInspection(Class toolClass) {
    ensureInitialized();
    myLocalInspectionTools.add(toolClass);
  }

  private void registerGlobalInspection(final Class aClass) {
    ensureInitialized();
    myGlobalInspectionTools.add(aClass);
  }

  private void registerInspectionTool(Class toolClass) {
    ensureInitialized();
    if (!myInspectionTools.contains(toolClass)) {
      myInspectionTools.add(toolClass);
    }
  }

  public InspectionTool[] createTools() {
    ensureInitialized();
    int ordinaryToolsSize = myInspectionTools.size();
    final int withLocal = ordinaryToolsSize + myLocalInspectionTools.size();
    InspectionTool[] tools = new InspectionTool[withLocal + myGlobalInspectionTools.size()];
    for (int i = 0; i < ordinaryToolsSize; i++) {
      tools[i] = (InspectionTool)instantiateTool(myInspectionTools.get(i));
    }
    for(int i = ordinaryToolsSize; i < withLocal; i++){
      tools[i] = new LocalInspectionToolWrapper((LocalInspectionTool)instantiateTool(myLocalInspectionTools.get(i - ordinaryToolsSize)));
    }
    for(int i = withLocal; i < tools.length; i++){
      tools[i] = new GlobalInspectionToolWrapper((GlobalInspectionTool)instantiateTool(myGlobalInspectionTools.get(i - withLocal)));
    }

    buildInspectionIndex(tools);

    return tools;
  }

  private static Object instantiateTool(Class toolClass) {
    try {
      Constructor constructor = toolClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      constructor.setAccessible(true);
      return constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    } catch (SecurityException e) {
      LOG.error(e);
    } catch (NoSuchMethodException e) {
      LOG.error(e);
    } catch (InstantiationException e) {
      LOG.error(e);
    } catch (IllegalAccessException e) {
      LOG.error(e);
    } catch (IllegalArgumentException e) {
      LOG.error(e);
    } catch (InvocationTargetException e) {
      LOG.error(e);
    }

    return null;
  }

  private synchronized void buildInspectionIndex(final InspectionTool[] tools) {
    if (myWords2InspectionToolNameMap == null) {
      myWords2InspectionToolNameMap = new HashMap<String, ArrayList<String>>();
      final Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode()) return;

      app.executeOnPooledThread(new Runnable(){
        public void run() {
          try {
            for (InspectionTool tool : tools) {
              processText(tool.getDisplayName().toLowerCase(), tool);
              final URL description = getDescriptionUrl(tool);
              if (description != null) {
                @NonNls String descriptionText = ResourceUtil.loadText(description).toLowerCase();
                if (descriptionText != null) {
                  descriptionText = HTML_PATTERN.matcher(descriptionText).replaceAll(" ");
                  processText(descriptionText, tool);
                }
              }
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  private static void processText(final @NonNls @NotNull String descriptionText, final InspectionTool tool) {
    final Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWordsWithoutStemming(descriptionText);
    for (String word : words) {
      ArrayList<String> descriptors = myWords2InspectionToolNameMap.get(word);
      if (descriptors == null) {
        descriptors = new ArrayList<String>();
        myWords2InspectionToolNameMap.put(word, descriptors);
      }
      descriptors.add(tool.getShortName());
    }
  }

  public static boolean isIndexBuild(){
    return myWords2InspectionToolNameMap != null;
  }

  public static List<String> getFilteredToolNames(String filter){
    return myWords2InspectionToolNameMap.get(filter);
  }

  public static Set<String> getToolWords(){
    return myWords2InspectionToolNameMap.keySet();
  }

  public static URL getDescriptionUrl(@NotNull InspectionProfileEntry tool) {
    Class aClass;
    if (tool instanceof LocalInspectionToolWrapper) {
      aClass = ((LocalInspectionToolWrapper)tool).getTool().getClass();
    }
    else if (tool instanceof GlobalInspectionToolWrapper) {
      aClass = ((GlobalInspectionToolWrapper)tool).getTool().getClass();
    }
    else {
      aClass = tool.getClass();
    }
    return ResourceUtil.getResource(aClass, "/inspectionDescriptions", ((InspectionTool)tool).getDescriptionFileName());
  }
}
