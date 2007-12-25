package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionTool;
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
import com.intellij.profile.codeInspection.ui.ErrorOptionsConfigurable;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @author max
 */
public class InspectionToolRegistrar implements ApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private ArrayList<Class> myInspectionTools;
  private ArrayList<Class> myLocalInspectionTools;
  private ArrayList<Class> myGlobalInspectionTools;

  private AtomicBoolean myToolsAreInitialized = new AtomicBoolean(false);

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
    if (!myToolsAreInitialized.getAndSet(true)) {
      final Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isHeadlessEnvironment()) return;

      app.executeOnPooledThread(new Runnable(){
        public void run() {
          for (InspectionTool tool : tools) {
            processText(tool.getDisplayName().toLowerCase(), tool);

            final String description = tool.loadDescription();
            if (description != null) {
              @NonNls String descriptionText;
              descriptionText = HTML_PATTERN.matcher(description).replaceAll(" ");
              processText(descriptionText, tool);
            }
          }
        }
      });
    }
  }

  private static void processText(final @NonNls @NotNull String descriptionText, final InspectionTool tool) {
    if (ApplicationManager.getApplication().isDisposed()) return;
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    LOG.assertTrue(optionsRegistrar != null);
    final Set<String> words = optionsRegistrar.getProcessedWordsWithoutStemming(descriptionText);
    for (String word : words) {
      optionsRegistrar.addOption(word, tool.getShortName(), tool.getDisplayName(), ErrorOptionsConfigurable.ID, ErrorOptionsConfigurable.DISPLAY_NAME);
    }
  }
}
