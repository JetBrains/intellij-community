package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Factory;
import com.intellij.profile.codeInspection.ui.InspectionToolsConfigurable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
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

  private final ArrayList<Factory<InspectionTool>> myInspectionToolFactories = new ArrayList<Factory<InspectionTool>>();
  private final ArrayList<Function<String, InspectionTool>> myToolsProviders = new ArrayList<Function<String, InspectionTool>>();

  private final AtomicBoolean myToolsAreInitialized = new AtomicBoolean(false);
  private final AtomicBoolean myInspectionComponentsLoaded = new AtomicBoolean(false);

  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  public void ensureInitialized() {
    if (!myInspectionComponentsLoaded.getAndSet(true)) {
      Set<InspectionToolProvider> providers = new THashSet<InspectionToolProvider>();
      providers.addAll(Arrays.asList(ApplicationManager.getApplication().getComponents(InspectionToolProvider.class)));
      providers.addAll(Arrays.asList(Extensions.getExtensions(InspectionToolProvider.EXTENSION_POINT_NAME)));
      registerTools(providers.toArray(new InspectionToolProvider[providers.size()]));
      for (InspectionToolsFactory factory : Extensions.getExtensions(InspectionToolsFactory.EXTENSION_POINT_NAME)) {
        for (final InspectionProfileEntry profileEntry : factory.createTools()) {
          myInspectionToolFactories.add(new Factory<InspectionTool>() {
            public InspectionTool create() {
              if (profileEntry instanceof LocalInspectionTool) {
                return new LocalInspectionToolWrapper((LocalInspectionTool)profileEntry);
              } else if (profileEntry instanceof GlobalInspectionTool) {
                return new GlobalInspectionToolWrapper((GlobalInspectionTool)profileEntry);
              }
              return (InspectionTool)profileEntry;
            }
          });
        }
      }
    }
  }

  public void registerTools(final InspectionToolProvider[] providers) {
    for (InspectionToolProvider provider : providers) {
      Class[] classes = provider.getInspectionClasses();
      for (Class aClass : classes) {
        registerInspectionTool(aClass, true);
      }
    }
  }

  private Factory<InspectionTool> registerInspectionTool(final Class aClass, boolean store) {
    if (LocalInspectionTool.class.isAssignableFrom(aClass)) {
      return registerLocalInspection(aClass, store);
    } else if (GlobalInspectionTool.class.isAssignableFrom(aClass)){
      return registerGlobalInspection(aClass, store);
    } else {
      ensureInitialized();
      return registerInspectionToolFactory(new Factory<InspectionTool>() {
        public InspectionTool create() {
          return (InspectionTool)instantiateTool(aClass);
        }
      }, store);
    }
  }

  public InspectionTool createInspectionTool(String shortName, final InspectionProfileEntry profileEntry) {
    for (Function<String, InspectionTool> toolsProvider : myToolsProviders) {
      final InspectionTool inspectionTool = toolsProvider.fun(shortName);
      if (inspectionTool != null) return inspectionTool;
    }
    final Class<? extends InspectionProfileEntry> inspectionToolClass;
    if (profileEntry instanceof LocalInspectionToolWrapper) {
      inspectionToolClass = ((LocalInspectionToolWrapper)profileEntry).getTool().getClass();
    }
    else if (profileEntry instanceof GlobalInspectionToolWrapper) {
      inspectionToolClass = ((GlobalInspectionToolWrapper)profileEntry).getTool().getClass();
    }
    else {
      inspectionToolClass = profileEntry.getClass();
    }
    return registerInspectionTool(inspectionToolClass, false).create();
  }

  public static InspectionToolRegistrar getInstance() {
    return ServiceManager.getService(InspectionToolRegistrar.class);
  }

  public Factory<InspectionTool> registerInspectionToolFactory(Factory<InspectionTool> factory) {
    return registerInspectionToolFactory(factory, true);
  }

  /**
   * make sure that it is not too late
   */
  public Factory<InspectionTool> registerInspectionToolFactory(Factory<InspectionTool> factory, boolean store) {
    if (store) {
      myInspectionToolFactories.add(factory);
    }
    return factory;
  }

  public Function<String, InspectionTool> registerInspectionToolProvider(Function<String, InspectionTool> provider) {
    myToolsProviders.add(provider);
    return provider;
  }

  private Factory<InspectionTool> registerLocalInspection(final Class toolClass, boolean store) {
    return registerInspectionToolFactory(new Factory<InspectionTool>() {
      public InspectionTool create() {
        return new LocalInspectionToolWrapper((LocalInspectionTool)instantiateTool(toolClass));
      }
    }, store);
  }

  private Factory<InspectionTool> registerGlobalInspection(final Class aClass, boolean store) {
    return registerInspectionToolFactory(new Factory<InspectionTool>() {
      public InspectionTool create() {
        return new GlobalInspectionToolWrapper((GlobalInspectionTool) instantiateTool(aClass));
      }
    }, store);
  }

  public InspectionTool[] createTools() {
    ensureInitialized();
    InspectionTool[] tools = new InspectionTool[myInspectionToolFactories.size()];
    for(int i=0; i<tools.length; i++) {
      ProgressManager.getInstance().checkCanceled();
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
      optionsRegistrar.addOption(word, tool.getShortName(), tool.getDisplayName(), InspectionToolsConfigurable.ID, InspectionToolsConfigurable.DISPLAY_NAME);
    }
  }
}
