package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Factory;
import com.intellij.profile.codeInspection.ui.ErrorOptionsConfigurable;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
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
public class InspectionToolRegistrar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private ArrayList<Factory<InspectionTool>> myInspectionToolFactories = new ArrayList<Factory<InspectionTool>>();

  private AtomicBoolean myToolsAreInitialized = new AtomicBoolean(false);
  private AtomicBoolean myInspectionComponentsLoaded = new AtomicBoolean(false);

  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  public void ensureInitialized() {
    if (!myInspectionComponentsLoaded.getAndSet(true)) {
      Set<InspectionToolProvider> providers = new THashSet<InspectionToolProvider>();
      providers.addAll(Arrays.asList(ApplicationManager.getApplication().getComponents(InspectionToolProvider.class)));
      providers.addAll(Arrays.asList(Extensions.getExtensions(InspectionToolProvider.EXTENSION_POINT_NAME)));
      registerTools(providers.toArray(new InspectionToolProvider[providers.size()]));
    }
  }

  public void registerTools(final InspectionToolProvider[] providers) {
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
    return ServiceManager.getService(InspectionToolRegistrar.class);
  }

  public void registerInspectionToolFactory(Factory<InspectionTool> factory) {
    myInspectionToolFactories.add(factory);
  }

  private void registerLocalInspection(final Class toolClass) {
    registerInspectionToolFactory(new Factory<InspectionTool>() {
      public InspectionTool create() {
        return new LocalInspectionToolWrapper((LocalInspectionTool)instantiateTool(toolClass));
      }
    });
  }

  private void registerGlobalInspection(final Class aClass) {
    registerInspectionToolFactory(new Factory<InspectionTool>() {
      public InspectionTool create() {
        return new GlobalInspectionToolWrapper((GlobalInspectionTool) instantiateTool(aClass));
      }
    });
  }

  private void registerInspectionTool(final Class toolClass) {
    registerInspectionToolFactory(new Factory<InspectionTool>() {
      public InspectionTool create() {
        return (InspectionTool)instantiateTool(toolClass);
      }
    });
    ensureInitialized();
  }

  public InspectionTool[] createTools() {
    ensureInitialized();
    InspectionTool[] tools = new InspectionTool[myInspectionToolFactories.size()];
    for(int i=0; i<tools.length; i++) {
      tools [i] = myInspectionToolFactories.get(i).create();
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
