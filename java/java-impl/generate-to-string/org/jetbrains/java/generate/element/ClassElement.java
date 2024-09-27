/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.element;

import com.intellij.openapi.util.text.StringUtil;

import java.util.Arrays;

/**
 * Information about the class that contains the fields that are target for the toString() code generation.
 *
 * Note: getters are accessed from Velocity templates, they aren't dead code
 */
@SuppressWarnings({"UnusedDeclaration"})
public class ClassElement {

    private String name;
    private String qualifiedName;
    private String superName;
    private String superQualifiedName;
    private String[] implementNames;
    private boolean deprecated;
    private boolean _enum;
    private boolean exception;
    private boolean _abstract;
  private int myTypeParams;

  /**
     * Does the class implement the given interface?
     * <p/>
     * The name should <b>not</b> be the qualified name.
     * <br/>The interface name can also be a comma seperated list to test against several interfaces. Will return true if the class implement just one of the interfaces.
     *
     * @param interfaceName  interface name.
     * @return   true if the class implements this interface, false if not.
     */
    public boolean isImplements(String interfaceName) {
        for (String className : implementNames) {
            if (interfaceName.contains(className)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Does the class extends any of the given classnames?
     *
     * @param classNames  list of classes seperated by comma.
     * @return  true if this class extends one of the given classnames.
     */
    public boolean isExtends(String classNames) {
        return classNames.contains(superName);
    }

    /**
     * Performs a regular expression matching the classname (getName()).
     *
     * @param regexp regular expression.
     * @return true if the classname matches the regular expression.
     * @throws IllegalArgumentException is throw if the given input is invalid (an empty String) or a pattern matching error.
     */
    public boolean matchName(String regexp) throws IllegalArgumentException {
        if (StringUtil.isEmpty(regexp)) {
            throw new IllegalArgumentException("Can't perform regular expression since the given input is empty. Check your Velocity template: regexp='" + regexp + "'");
        }
        return name.matches(regexp);
    }

    public String[] getImplementNames() {
        return implementNames;
    }

    public void setImplementNames(String[] implementNames) {
        this.implementNames = implementNames;
    }

    public String getSuperQualifiedName() {
        return superQualifiedName;
    }

    public void setSuperQualifiedName(String superQualifiedName) {
        this.superQualifiedName = superQualifiedName;
    }

    public String getSuperName() {
        return superName;
    }

    public void setSuperName(String superName) {
        this.superName = superName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String FQClassname) {
        this.qualifiedName = FQClassname;
    }

    public boolean isHasSuper() {
        return this.superName != null;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public boolean isEnum() {
        return _enum;
    }

    public void setEnum(boolean aEnum) {
        this._enum = aEnum;
    }

    public boolean isException() {
        return exception;
    }

    public void setException(boolean exception) {
        this.exception = exception;
    }

    public boolean isAbstract() {
        return _abstract;
    }

    public void setAbstract(boolean aAbstract) {
        this._abstract = aAbstract;
    }

    public String toString() {
        return "ClassElement{" +
                "name='" + name + "'" +
                ", qualifiedName='" + qualifiedName + "'" +
                ", superName='" + superName + "'" +
                ", superQualifiedName='" + superQualifiedName + "'" +
                ", implementNames=" + (implementNames == null ? null : Arrays.asList(implementNames)) +
                ", enum=" + _enum +
                ", deprecated=" + deprecated +
                ", exception=" + exception +
                ", abstract=" + _abstract +
                "}";
    }

    public void setTypeParams(int typeParams) {
      myTypeParams = typeParams;
    }
  
    public int getTypeParams() {
      return myTypeParams;
    }
}
