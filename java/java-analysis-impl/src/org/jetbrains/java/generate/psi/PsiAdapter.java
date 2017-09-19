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
package org.jetbrains.java.generate.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.*;

/**
 * Basic PSI Adapter with common function that works in all supported versions of IDEA.
 */
public class PsiAdapter {

    private PsiAdapter() {}

    /**
     * Returns true if a field is constant.
     * <p/>
     * This is identified as the name of the field is only in uppercase and it has
     * a {@code static} modifier.
     *
     * @param field field to check if it's a constant
     * @return true if constant.
     */
    public static boolean isConstantField(PsiField field) {
        PsiModifierList list = field.getModifierList();
        if (list == null) {
            return false;
        }

        // modifier must be static
        if (!list.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        // name must NOT have any lowercase character
        return !StringUtil.hasLowerCaseChar(field.getName());
    }

    /**
     * Finds an existing method with the given name.
     * If there isn't a method with the name, null is returned.
     *
     * @param clazz the class
     * @param name  name of method to find
     * @return the found method, null if none exist
     */
    @Nullable
    public static PsiMethod findMethodByName(PsiClass clazz, String name) {
        PsiMethod[] methods = clazz.getMethods();

        // use reverse to find from bottom as the duplicate conflict resolution policy requires this
        for (int i = methods.length - 1; i >= 0; i--) {
            PsiMethod method = methods[i];
            if (name.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    /**
     * Returns true if the given field a primitive array type (e.g., int[], long[], float[]).
     *
     * @param type type.
     * @return true if field is a primitive array type.
     */
    public static boolean isPrimitiveArrayType(PsiType type) {
        return type instanceof PsiArrayType && isPrimitiveType(((PsiArrayType) type).getComponentType());
    }

    /**
     * Is the type an Object array type (etc. String[], Object[])?
     *
     * @param type type.
     * @return true if it's an Object array type.
     */
    public static boolean isObjectArrayType(PsiType type) {
        return type instanceof PsiArrayType && !isPrimitiveType(((PsiArrayType) type).getComponentType());
    }

    /**
     * Is the type a String array type (etc. String[])?
     *
     * @param type type.
     * @return true if it's a String array type.
     */
    public static boolean isStringArrayType(PsiType type) {
        if (isPrimitiveType(type))
            return false;

        return type.getCanonicalText().indexOf("String[]") > 0;
    }

    /**
     * Is the given field a {@link java.util.Collection} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Collection type.
     */
    public static boolean isCollectionType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Collection");
    }

    /**
     * Is the given field a {@link java.util.Map} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Map type.
     */
    public static boolean isMapType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, JAVA_UTIL_MAP);
    }

    /**
     * Is the given field a {@link java.util.Set} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Map type.
     */
    public static boolean isSetType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, JAVA_UTIL_SET);
    }

    /**
     * Is the given field a {@link java.util.List} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Map type.
     */
    public static boolean isListType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, JAVA_UTIL_LIST);
    }

    /**
     * Is the given field a {@link java.lang.String} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a String type.
     */
    public static boolean isStringType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, JAVA_LANG_STRING);
    }

    /**
     * Is the given field assignable from {@link java.lang.Object}?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's an Object type.
     */
    public static boolean isObjectType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, JAVA_LANG_OBJECT);
    }

    /**
     * Is the given field a {@link java.util.Date} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Date type.
     */
    public static boolean isDateType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Date");
    }

    /**
     * Is the given field a {@link java.util.Calendar} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Calendar type.
     */
    public static boolean isCalendarType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Calendar");
    }

    /**
     * Is the given field a {@link java.lang.Boolean} type or a primitive boolean type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Boolean or boolean type.
     */
    public static boolean isBooleanType(PsiElementFactory factory, PsiType type) {
        if (isPrimitiveType(type)) {
            // test for simple type of boolean
            String s = type.getCanonicalText();
            return "boolean".equals(s);
        } else {
            // test for Object type of Boolean
            return isTypeOf(factory, type, JAVA_LANG_BOOLEAN);
        }
    }

    /**
     * Is the given field a numeric type (assignable from java.lang.Numeric or a primitive type of byte, short, int, long, float, double type)?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a numeric type.
     */
    public static boolean isNumericType(PsiElementFactory factory, PsiType type) {
        if (isPrimitiveType(type)) {
            // test for simple type of numeric
            String s = type.getCanonicalText();
            return "byte".equals(s) || "double".equals(s) || "float".equals(s) || "int".equals(s) || "long".equals(s) || "short".equals(s);
        } else {
            // test for Object type of numeric
            return isTypeOf(factory, type, "java.lang.Number");
        }
    }

    /**
     * Does the javafile have the import statement?
     *
     * @param javaFile        javafile.
     * @param importStatement import statement to test existing for.
     * @return true if the javafile has the import statement.
     */
    public static boolean hasImportStatement(PsiJavaFile javaFile, String importStatement) {
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }

        if (importStatement.endsWith(".*")) {
            return importList.findOnDemandImportStatement(fixImportStatement(importStatement)) != null;
        } else {
            return importList.findSingleClassImportStatement(importStatement) != null;
        }
    }

    /**
     * Adds an import statement to the javafile and optimizes the imports afterwards.
     *
     *
     * @param javaFile                javafile.
     * @param importStatementOnDemand name of import statement, must be with a wildcard (etc. java.util.*).
     * @throws com.intellij.util.IncorrectOperationException
     *          is thrown if there is an error creating the import statement.
     */
    public static void addImportStatement(PsiJavaFile javaFile, String importStatementOnDemand) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(javaFile.getProject()).getElementFactory();
        PsiImportStatement is = factory.createImportStatementOnDemand(fixImportStatement(importStatementOnDemand));

        // add the import to the file, and optimize the imports
        PsiImportList importList = javaFile.getImportList();
        if (importList != null) {
            importList.add(is);
        }

        JavaCodeStyleManager.getInstance(javaFile.getProject()).optimizeImports(javaFile);
    }

    /**
     * Fixes the import statement to be returned as packagename only (without .* or any Classname).
     * <p/>
     * <br/>Example: java.util will be returned as java.util
     * <br/>Example: java.util.* will be returned as java.util
     * <br/>Example: java.text.SimpleDateFormat will be returned as java.text
     *
     * @param importStatementOnDemand import statement
     * @return import statement only with packagename
     */
    private static String fixImportStatement(String importStatementOnDemand) {
        if (importStatementOnDemand.endsWith(".*")) {
            return importStatementOnDemand.substring(0, importStatementOnDemand.length() - 2);
        } else {
            boolean hasClassname = StringUtil.hasUpperCaseChar(importStatementOnDemand);

            if (hasClassname) {
                // extract packagename part
                int pos = importStatementOnDemand.lastIndexOf(".");
                return importStatementOnDemand.substring(0, pos);
            } else {
                // it is a pure packagename
                return importStatementOnDemand;
            }
        }
    }

    /**
     * Gets the fields fully qualified classname (etc java.lang.String, java.util.ArrayList)
     *
     * @param type the type.
     * @return the fully qualified classname, null if the field is a primitive.
     * @see #getTypeClassName(com.intellij.psi.PsiType) for the non qualified version.
     */
    @Nullable
    public static String getTypeQualifiedClassName(PsiType type) {
        if (isPrimitiveType(type)) {
            return null;
        }

        // avoid [] if the type is an array
        String name = type.getCanonicalText();
        if (name.endsWith("[]")) {
            return name.substring(0, name.length() - 2);
        }

        return name;
    }

    /**
     * Gets the fields classname (etc. String, ArrayList)
     *
     * @param type the type.
     * @return the classname, null if the field is a primitive.
     * @see #getTypeQualifiedClassName(com.intellij.psi.PsiType) for the qualified version.
     */
    @Nullable
    public static String getTypeClassName(PsiType type) {
        String name = getTypeQualifiedClassName(type);

        // return null if it was a primitive type
        if (name == null) {
            return null;
        }

        return StringUtil.getShortName(name);
    }

    /**
     * Finds the public static void main(String[] args) method.
     *
     * @param clazz the class.
     * @return the method if it exists, null if not.
     */
    @Nullable
    public static PsiMethod findPublicStaticVoidMainMethod(PsiClass clazz) {
        PsiMethod[] methods = clazz.findMethodsByName("main", false);

        // is it public static void main(String[] args)
        for (PsiMethod method : methods) {
            // must be public
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            // must be static
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            // must have void as return type
            PsiType returnType = method.getReturnType();
            if (!PsiType.VOID.equals(returnType)) {
                continue;
            }

            // must have one parameter
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }

            // parameter must be string array
            if (!isStringArrayType(parameters[0].getType())) {
                continue;
            }

            // public static void main(String[] args) method found
            return method;
        }

        // main not found
        return null;
    }

    /**
     * Add or replaces the javadoc comment to the given method.
     *
     * @param method           the method the javadoc should be added/set to.
     * @param javadoc          the javadoc comment.
     * @param replace          true if any existing javadoc should be replaced. false will not replace any existing javadoc and thus leave the javadoc untouched.
     * @return the added/replace javadoc comment, null if the was an existing javadoc and it should <b>not</b> be replaced.
     * @throws IncorrectOperationException is thrown if error adding/replacing the javadoc comment.
     */
    @Nullable
    public static PsiComment addOrReplaceJavadoc(PsiMethod method, String javadoc, boolean replace) {
        final Project project = method.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiComment comment = factory.createCommentFromText(javadoc, null);

        // does a method already exists?
        PsiDocComment doc = method.getDocComment();
        if (doc != null) {
            if (replace) {
                // javadoc already exists, so replace
                doc.replace(comment);
                final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
                codeStyleManager.reformat(method); // to reformat javadoc
                return comment;
            } else {
                // do not replace existing javadoc
                return null;
            }
        } else {
            // add new javadoc
            method.addBefore(comment, method.getFirstChild());
            final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            codeStyleManager.reformat(method); // to reformat javadoc
            return comment;
        }
    }

    /**
     * Is the given type a "void" type.
     *
     * @param type the type.
     * @return true if a void type, false if not.
     */
    public static boolean isTypeOfVoid(PsiType type) {
        return type != null && type.equalsToText("void");
    }

    /**
     * Is the method a getter method?
     * <p/>
     * The name of the method must start with {@code get} or {@code is}.
     * And if the method is a {@code isXXX} then the method must return a java.lang.Boolean or boolean.
     *
     *
     * @param method  the method
     * @return true if a getter method, false if not.
     */
    public static boolean isGetterMethod(PsiMethod method) {
        // must not be a void method
        if (isTypeOfVoid(method.getReturnType())) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 0) {
            return false;
        }
        return true;
    }

    /**
     * Gets the field name of the getter method.
     * <p/>
     * The method must be a getter method for a field.
     * Returns null if this method is not a getter.
     * <p/>
     * The fieldname is the part of the name that is after the {@code get} or {@code is} part
     * of the name.
     * <p/>
     * Example: methodName=getName will return fieldname=name
     *
     *
     * @param method  the method
     * @return the fieldname if this is a getter method.
     * @see #isGetterMethod(com.intellij.psi.PsiMethod) for the getter check
     */
    @Nullable
    public static String getGetterFieldName(PsiMethod method) {
        // must be a getter
        if (!isGetterMethod(method)) {
            return null;
        }
        return PropertyUtilBase.getPropertyNameByGetter(method);
    }

    /**
     * Returns true if the field is enum (JDK1.5).
     *
     * @param field   field to check if it's a enum
     * @return true if enum.
     */
    public static boolean isEnumField(PsiField field) {
        PsiType type = field.getType();
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        return (aClass != null) && aClass.isEnum();
    }

    /**
     * Is the class an exception - extends Throwable (will check super).
     *
     * @param clazz class to check.
     * @return true if class is an exception.
     */
    public static boolean isExceptionClass(PsiClass clazz) {
      return InheritanceUtil.isInheritor(clazz, JAVA_LANG_THROWABLE);
    }

    /**
     * Finds the public boolean equals(Object o) method.
     *
     * @param clazz the class.
     * @return the method if it exists, null if not.
     */
    @Nullable
    public static PsiMethod findEqualsMethod(PsiClass clazz) {
        PsiMethod[] methods = clazz.findMethodsByName("equals", false);

        // is it public boolean equals(Object o)
        for (PsiMethod method : methods) {
            // must be public
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            // must not be static
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            // must have boolean as return type
            PsiType returnType = method.getReturnType();
            if (!PsiType.BOOLEAN.equals(returnType)) {
                continue;
            }

            // must have one parameter
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }

            // parameter must be Object
            if (!(parameters[0].getType().getCanonicalText().equals(JAVA_LANG_OBJECT))) {
                continue;
            }

            // equals method found
            return method;
        }

        // equals not found
        return null;
    }

    /**
     * Finds the public int hashCode() method.
     *
     * @param clazz the class.
     * @return the method if it exists, null if not.
     */
    @Nullable
    public static PsiMethod findHashCodeMethod(PsiClass clazz) {
        PsiMethod[] methods = clazz.findMethodsByName("hashCode", false);

        // is it public int hashCode()
        for (PsiMethod method : methods) {
            // must be public
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            // must not be static
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            // must have int as return type
            PsiType returnType = method.getReturnType();
            if (!PsiType.INT.equals(returnType)) {
                continue;
            }

            // must not have a parameter
            PsiParameterList parameters = method.getParameterList();
            if (parameters.getParametersCount() != 0) {
                continue;
            }

            // hashCode method found
            return method;
        }

        // hashCode not found
        return null;
    }

    /**
     * Check if the given type against a FQ classname (assignable).
     *
     * @param factory         IDEA factory
     * @param type            the type
     * @param typeFQClassName the FQ classname to test against.
     * @return true if the given type is assignable of FQ classname.
     */
    protected static boolean isTypeOf(PsiElementFactory factory, PsiType type, String typeFQClassName) {
        // fix for IDEA where fields can have 'void' type and generate NPE.
        if (isTypeOfVoid(type)) {
            return false;
        }

        if (isPrimitiveType(type)) {
            return false;
        }

        GlobalSearchScope scope = type.getResolveScope();
        if (scope == null) {
            return false;
        }
        PsiType typeTarget = factory.createTypeByFQClassName(typeFQClassName, scope);
        return typeTarget.isAssignableFrom(type);
    }

    /**
     * Gets the names the given class implements (not FQ names).
     *
     * @param clazz the class
     * @return the names.
     */
    @NotNull
    public static String[] getImplementsClassnames(PsiClass clazz) {
        PsiClass[] interfaces = clazz.getInterfaces();

        if (interfaces == null || interfaces.length == 0) {
          return ArrayUtil.EMPTY_STRING_ARRAY;
        }

        String[] names = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            PsiClass anInterface = interfaces[i];
            names[i] = anInterface.getName();
        }

        return names;
    }

    /**
     * Is the given type a primitive?
     *
     * @param type the type.
     * @return true if primitive, false if not.
     */
    public static boolean isPrimitiveType(PsiType type) {
        return type instanceof PsiPrimitiveType;
    }

  public static int getJavaVersion(@NotNull PsiElement element) {
    JavaSdkVersion sdkVersion = JavaVersionService.getInstance().getJavaSdkVersion(element);
    if (sdkVersion == null) {
      sdkVersion = JavaSdkVersion.fromLanguageLevel(PsiUtil.getLanguageLevel(element));
    }

    int version = 0;
    switch (sdkVersion) {
      case JDK_1_0:
      case JDK_1_1:
        version = 1;
        break;
      case JDK_1_2:
        version = 2;
        break;
      case JDK_1_3:
        version = 3;
        break;
      case JDK_1_4:
        version = 4;
        break;
      case JDK_1_5:
        version = 5;
        break;
      case JDK_1_6:
        version = 6;
        break;
      case JDK_1_7:
        version = 7;
        break;
      case JDK_1_8:
        version = 8;
        break;
      case JDK_1_9:
        version = 9;
        break;
    }
    return version;
  }

  public static boolean isNestedArray(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)aType).getComponentType();
    return componentType instanceof PsiArrayType;
  }
}
