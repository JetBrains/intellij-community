// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decorating component adapter that can be used to set additional properties
 * on a component in a bean style. These properties must be managed manually
 * by the user of the API, and will not be managed by PicoContainer.
 * <p/>
 * This adapter is mostly handy for setting various primitive properties via setters;
 * it is also able to set javabean properties by discovering an appropriate
 * {@link PropertyEditor} and using its <code>setAsText</code> method.
 * <p/>
 * <em>
 * Note that this class doesn't cache instances. If you want caching,
 * use a {@link CachingComponentAdapter} around this one.
 * </em>
 *
 * @author Aslak Helles&oslash;y
 * @version $Revision: 2793 $
 * @since 1.0
 */
public class BeanPropertyComponentAdapter extends DecoratingComponentAdapter {
  private Map properties;
  private transient Map setters = null;

  /**
   * Construct a BeanPropertyComponentAdapter.
   *
   * @param delegate the wrapped {@link ComponentAdapter}
   * @throws PicoInitializationException {@inheritDoc}
   */
  public BeanPropertyComponentAdapter(ComponentAdapter delegate) throws PicoInitializationException {
    super(delegate);
  }

  /**
   * Get a component instance and set given property values.
   *
   * @return the component instance with any properties of the properties map set.
   * @throws PicoInitializationException        {@inheritDoc}
   * @throws PicoIntrospectionException         {@inheritDoc}
   * @throws AssignabilityRegistrationException {@inheritDoc}
   * @throws NotConcreteRegistrationException   {@inheritDoc}
   * @see #setProperties(Map)
   */
  @Override
  public Object getComponentInstance(PicoContainer container)
    throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
    final Object componentInstance = super.getComponentInstance(container);
    if (setters == null) {
      setters = getSetters(getComponentImplementation());
    }

    if (properties != null) {
      Set propertyNames = properties.keySet();
      for (Object name : propertyNames) {
        final String propertyName = (String)name;
        final Object propertyValue = properties.get(propertyName);
        Method setter = (Method)setters.get(propertyName);

        Object valueToInvoke = this.getSetterParameter(propertyName, propertyValue, componentInstance, container);

        try {
          setter.invoke(componentInstance, valueToInvoke);
        }
        catch (final Exception e) {
          throw new PicoInitializationException(
            "Failed to set property " + propertyName + " to " + propertyValue + ": " + e.getMessage(),
            e);
        }
      }
    }
    return componentInstance;
  }

  private Map getSetters(Class clazz) {
    Map result = new HashMap();
    Method[] methods = getMethods(clazz);
    for (Method method : methods) {
      if (isSetter(method)) {
        result.put(getPropertyName(method), method);
      }
    }
    return result;
  }

  private Method[] getMethods(final Class clazz) {
    return (Method[])AccessController.doPrivileged(new PrivilegedAction() {
      @Override
      public Object run() {
        return clazz.getMethods();
      }
    });
  }


  private static String getPropertyName(Method method) {
    final String name = method.getName();
    String result = name.substring(3);
    if (result.length() > 1 && !Character.isUpperCase(result.charAt(1))) {
      result = Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }
    else if (result.length() == 1) {
      result = result.toLowerCase(Locale.ENGLISH);
    }
    return result;
  }

  private static boolean isSetter(Method method) {
    final String name = method.getName();
    return name.length() > 3 &&
           name.startsWith("set") &&
           method.getParameterTypes().length == 1;
  }


  private static Object convertType(PicoContainer container, Method setter, String propertyValue) throws ClassNotFoundException {
    if (propertyValue == null) {
      return null;
    }
    Class type = setter.getParameterTypes()[0];
    String typeName = type.getName();

    Object result = convert(typeName, propertyValue, Thread.currentThread().getContextClassLoader());

    if (result == null) {

      // check if the propertyValue is a key of a component in the container
      // if so, the typeName of the component and the setters parameter typeName
      // have to be compatible

      // TODO: null check only because of test-case, otherwise null is impossible
      if (container != null) {
        Object component = container.getComponentInstance(propertyValue);
        if (component != null && type.isAssignableFrom(component.getClass())) {
          return component;
        }
      }
    }
    return result;
  }

  /**
   * Converts a String value of a named type to an object.
   * Works with primitive wrappers, String, File, URL types, or any type that has
   * an appropriate {@link PropertyEditor}.
   *
   * @param typeName    name of the type
   * @param value       its value
   * @param classLoader used to load a class if typeName is "class" or "java.lang.Class" (ignored otherwise)
   * @return instantiated object or null if the type was unknown/unsupported
   * @throws ClassNotFoundException if typeName is "class" or "java.lang.Class" and class couldn't be loaded.
   */
  public static Object convert(String typeName, String value, ClassLoader classLoader) throws ClassNotFoundException {
    if (typeName.equals(Boolean.class.getName()) || typeName.equals(boolean.class.getName())) {
      return Boolean.valueOf(value);
    }
    else if (typeName.equals(Byte.class.getName()) || typeName.equals(byte.class.getName())) {
      return Byte.valueOf(value);
    }
    else if (typeName.equals(Short.class.getName()) || typeName.equals(short.class.getName())) {
      return Short.valueOf(value);
    }
    else if (typeName.equals(Integer.class.getName()) || typeName.equals(int.class.getName())) {
      return Integer.valueOf(value);
    }
    else if (typeName.equals(Long.class.getName()) || typeName.equals(long.class.getName())) {
      return Long.valueOf(value);
    }
    else if (typeName.equals(Float.class.getName()) || typeName.equals(float.class.getName())) {
      return Float.valueOf(value);
    }
    else if (typeName.equals(Double.class.getName()) || typeName.equals(double.class.getName())) {
      return Double.valueOf(value);
    }
    else if (typeName.equals(Character.class.getName()) || typeName.equals(char.class.getName())) {
      return new Character(value.toCharArray()[0]);
    }
    else if (typeName.equals(String.class.getName()) || typeName.equals("string")) {
      return value;
    }
    else if (typeName.equals(File.class.getName()) || typeName.equals("file")) {
      return new File(value);
    }
    else if (typeName.equals(URL.class.getName()) || typeName.equals("url")) {
      try {
        return new URL(value);
      }
      catch (MalformedURLException e) {
        throw new PicoInitializationException(e);
      }
    }
    else if (typeName.equals(Class.class.getName()) || typeName.equals("class")) {
      return classLoader.loadClass(value);
    }
    else {
      final Class clazz = classLoader.loadClass(typeName);
      final PropertyEditor editor = PropertyEditorManager.findEditor(clazz);
      if (editor != null) {
        editor.setAsText(value);
        return editor.getValue();
      }
    }
    return null;
  }

  /**
   * Sets the bean property values that should be set upon creation.
   *
   * @param properties bean properties
   */
  public void setProperties(Map properties) {
    this.properties = properties;
  }

  /**
   * Converts and validates the given property value to an appropriate object
   * for calling the bean's setter.
   *
   * @param propertyName      String the property name on the component that
   *                          we will be setting the value to.
   * @param propertyValue     Object the property value that we've been given. It
   *                          may need conversion to be formed into the value we need for the
   *                          component instance setter.
   * @param componentInstance the component that we're looking to provide
   *                          the setter to.
   * @return Object: the final converted object that can
   * be used in the setter.
   */
  private Object getSetterParameter(final String propertyName, final Object propertyValue,
                                    final Object componentInstance, PicoContainer container)
    throws PicoInitializationException, ClassCastException {

    if (propertyValue == null) {
      return null;
    }

    Method setter = (Method)setters.get(propertyName);

    //We can assume that there is only one object (as per typical setters)
    //because the Setter introspector does that job for us earlier.
    Class setterParameter = setter.getParameterTypes()[0];

    Object convertedValue;

    Class givenParameterClass = propertyValue.getClass();

    //
    //If property value is a string or a true primative then convert it to whatever
    //we need.  (String will convert to string).
    //
    try {
      convertedValue = convertType(container, setter, propertyValue.toString());
    }
    catch (ClassNotFoundException e) {
      throw new PicoInvocationTargetInitializationException(e);
    }

    //Otherwise, check the parameter type to make sure we can
    //assign it properly.
    if (convertedValue == null) {
      if (setterParameter.isAssignableFrom(givenParameterClass)) {
        convertedValue = propertyValue;
      }
      else {
        throw new ClassCastException("Setter: " + setter.getName() + " for component: "
                                     + componentInstance.toString() + " can only take objects of: " + setterParameter.getName()
                                     + " instead got: " + givenParameterClass.getName());
      }
    }
    return convertedValue;
  }
}
