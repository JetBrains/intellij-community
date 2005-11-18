package com.intellij.codeInspection.ex;

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

  public InspectionToolRegistrar(InspectionToolProvider[] providers) {
    myInspectionTools = new ArrayList<Class>();
    myLocalInspectionTools = new ArrayList<Class>();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      for (InspectionToolProvider provider : providers) {
        Class[] classes = provider.getInspectionClasses();
        for (Class aClass : classes) {
          if (LocalInspectionTool.class.isAssignableFrom(aClass)) {
            registerLocalInspection(aClass);
          }
          else {
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

  private void registerInspectionTool(Class toolClass) {
    if (myInspectionTools.contains(toolClass)) return;
    myInspectionTools.add(toolClass);
  }

  public InspectionTool[] createTools() {
    int ordinaryToolsSize = myInspectionTools.size();
    InspectionTool[] tools = new InspectionTool[ordinaryToolsSize + myLocalInspectionTools.size()];
    for (int i = 0; i < tools.length; i++) {
      tools[i] = i < ordinaryToolsSize
                 ? instantiateTool(myInspectionTools.get(i))
                 : new LocalInspectionToolWrapper(instantiateLocalTool(myLocalInspectionTools.get(i - ordinaryToolsSize)));
    }

    return tools;
  }

  private static LocalInspectionTool instantiateLocalTool(Class toolClass) {
    try {
      Constructor constructor = toolClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      Object[] args = ArrayUtil.EMPTY_OBJECT_ARRAY;
      return (LocalInspectionTool) constructor.newInstance(args);
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

  public LocalInspectionTool[] createLocalTools() {
    LocalInspectionTool[] tools = new LocalInspectionTool[myLocalInspectionTools.size()];
    for (int i = 0; i < tools.length; i++) {
      tools[i] = instantiateLocalTool(myLocalInspectionTools.get(i));
    }

    return tools;
  }
}
