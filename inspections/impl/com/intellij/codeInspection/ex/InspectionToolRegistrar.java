package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * @author max
 */
public class InspectionToolRegistrar implements ApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private final ArrayList<Class> myInspectionTools;
  private final ArrayList<Class> myLocalInspectionTools;
  private final ArrayList<Class> myGlobalInspectionTools;

  public InspectionToolRegistrar(InspectionToolProvider[] providers) {
    myInspectionTools = new ArrayList<Class>();
    myLocalInspectionTools = new ArrayList<Class>();
    myGlobalInspectionTools = new ArrayList<Class>();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
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
  }

  public static InspectionToolRegistrar getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionToolRegistrar.class);
  }

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
    myLocalInspectionTools.add(toolClass);
  }

  private void registerGlobalInspection(final Class aClass) {
    myGlobalInspectionTools.add(aClass);
  }

  private void registerInspectionTool(Class toolClass) {
    if (myInspectionTools.contains(toolClass)) return;
    myInspectionTools.add(toolClass);
  }

  public InspectionTool[] createTools() {
    int ordinaryToolsSize = myInspectionTools.size();
    final int withLocal = ordinaryToolsSize + myLocalInspectionTools.size();
    InspectionTool[] tools = new InspectionTool[withLocal + myGlobalInspectionTools.size()];
    for (int i = 0; i < ordinaryToolsSize; i++) {
      tools[i] = instantiateTool(myInspectionTools.get(i));
    }
    for(int i = ordinaryToolsSize; i < withLocal; i++){
      tools[i] = new LocalInspectionToolWrapper((LocalInspectionTool)instantiateWrapper(myLocalInspectionTools.get(i - ordinaryToolsSize)));
    }
    for(int i = withLocal; i < tools.length; i++){
      tools[i] = new GlobalInspectionToolWrapper((GlobalInspectionTool)instantiateWrapper(myGlobalInspectionTools.get(i - withLocal)));
    }

    return tools;
  }

  private static Object instantiateWrapper(Class toolClass) {
    try {
      Constructor constructor = toolClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      Object[] args = ArrayUtil.EMPTY_OBJECT_ARRAY;
      return constructor.newInstance(args);
    } catch (NoSuchMethodException e) {
      LOG.error(e);
    } catch (SecurityException e) {
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

  private static InspectionTool instantiateTool(Class toolClass) {
    try {
      Constructor constructor = toolClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      constructor.setAccessible(true);
      return (InspectionTool) constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
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
}
