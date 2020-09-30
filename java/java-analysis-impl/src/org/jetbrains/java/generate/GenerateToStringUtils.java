/*
 * Copyright 2001-2014 the original author or authors.
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
package org.jetbrains.java.generate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.config.FilterPattern;
import org.jetbrains.java.generate.psi.PsiAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Utility methods for GenerationToStringAction and the inspections.
 */
public final class GenerateToStringUtils {

    private static final Logger log = Logger.getInstance("#GenerateToStringUtils");

    private GenerateToStringUtils() {}

    /**
     * Filters the list of fields from the class with the given parameters from the {@link Config config} settings.
     *
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return fields available for this action after the filter process.
     */
    public static PsiField @NotNull [] filterAvailableFields(PsiClass clazz, FilterPattern pattern) {
        return filterAvailableFields(clazz, false, pattern);
    }

    /**
     * Filters the list of fields from the class with the given parameters from the {@link Config config} settings.
     *
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return fields available for this action after the filter process.
     */
    public static PsiField @NotNull [] filterAvailableFields(PsiClass clazz,
                                                             boolean includeSuperClass,
                                                             FilterPattern pattern) {
        if (log.isDebugEnabled()) log.debug("Filtering fields using the pattern: " + pattern);
        List<PsiField> availableFields = new ArrayList<>();
        collectAvailableFields(clazz, clazz, includeSuperClass, pattern, availableFields, new HashSet<>());
        return availableFields.toArray(PsiField.EMPTY_ARRAY);
    }

    private static void collectAvailableFields(PsiClass clazz,
                                               PsiElement place,
                                               boolean includeSuperClass,
                                               FilterPattern pattern,
                                               List<? super PsiField> availableFields,
                                               HashSet<? super PsiClass> visited) {

        int sortElements = GenerateToStringContext.getConfig().getSortElements();

        if (includeSuperClass && sortElements == 3) {
            PsiClass superClass = clazz.getSuperClass();
            if (superClass != null && visited.add(superClass)) {
                collectAvailableFields(superClass, place, true, pattern, availableFields, visited);
            }
        }

        // performs til filtering process
        PsiField[] fields = includeSuperClass && sortElements != 3 ? clazz.getAllFields() : clazz.getFields();
        for (PsiField field : fields) {
            if (!JavaResolveUtil.isAccessible(field, field.getContainingClass(), field.getModifierList(), place, null, null)) {
                continue;
            }
            // if the field matches the pattern then it shouldn't be in the list of available fields
            if (!pattern.fieldMatches(field)) {
                availableFields.add(field);
            }
        }
    }

    /**
     * Filters the list of methods from the class to be
     * <ul>
     * <li/>a getter method (java bean compliant)
     * <li/>should not be a getter for an existing field
     * <li/>public, non static, non abstract
     * <ul/>
     *
     *
     * @param clazz          the class to filter it's fields
     * @param pattern        the filter pattern to filter out unwanted fields
     * @return methods available for this action after the filter process.
     */
    public static PsiMethod @NotNull [] filterAvailableMethods(PsiClass clazz, @NotNull FilterPattern pattern) {
        if (log.isDebugEnabled()) log.debug("Filtering methods using the pattern: " + pattern);
        List<PsiMethod> availableMethods = new ArrayList<>();
        collectAvailableMethods(clazz, clazz, pattern, availableMethods, new HashSet<>());
        return availableMethods.toArray(PsiMethod.EMPTY_ARRAY);
    }

    private static void collectAvailableMethods(PsiClass clazz,
                                                PsiClass base,
                                                @NotNull FilterPattern pattern,
                                                List<? super PsiMethod> availableMethods,
                                                HashSet<? super PsiClass> visited) {
        int sortElements = GenerateToStringContext.getConfig().getSortElements();
        if (sortElements == 3) {
            PsiClass superClass = clazz.getSuperClass();
            if (superClass != null && visited.add(superClass)) {
                collectAvailableMethods(superClass, base, pattern, availableMethods, visited);
            }
        }
        PsiMethod[] methods = sortElements != 3 ? clazz.getAllMethods() : clazz.getMethods();
        for (PsiMethod method : methods) {
            // the method should be a getter
            if (!PsiAdapter.isGetterMethod(method)) {
                continue;
            }

            // must not return void
            final PsiType returnType = method.getReturnType();
            if (returnType == null || PsiType.VOID.equals(returnType)) {
                continue;
            }

            // method should be public, non static, non abstract
            if (!method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.STATIC) ||
                method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }

            // method should not be a getter for an existing field in the same class
            String fieldName = PsiAdapter.getGetterFieldName(method);
            if (base.findFieldByName(fieldName, false) != null) {
                continue;
            }

            // must not be named toString or getClass
            final String methodName = method.getName();
            if ("toString".equals(methodName) || "getClass".equals(methodName) || "hashCode".equals(methodName)) {
                continue;
            }

            // if the method matches the pattern then it shouldn't be in the list of available methods
            if (pattern.methodMatches(method)) {
                continue;
            }

            if (log.isDebugEnabled())
                log.debug("Adding the method " + methodName + " as there is not a field for this getter");
            availableMethods.add(method);
        }
    }
}
