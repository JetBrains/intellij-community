/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.intellij.diagnostic.hprof.classstore

import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.parser.Type
import com.intellij.diagnostic.hprof.visitors.CollectStringValuesVisitor
import com.intellij.diagnostic.hprof.visitors.CreateClassStoreVisitor
import gnu.trove.TLongObjectHashMap
import java.util.function.LongUnaryOperator

class ClassStore(private val classes: TLongObjectHashMap<ClassDefinition>) {
  private val stringToClassDefinition = HashMap<String, ClassDefinition>()
  private val classDefinitionToShortPrettyName = HashSet<ClassDefinition>()

  val softReferenceClass: ClassDefinition
  val weakReferenceClass: ClassDefinition

  val classClass: ClassDefinition

  private val primitiveArrayToClassDefinition = HashMap<Type, ClassDefinition>()

  init {
    fun getClashedNameWithIndex(classDefinition: ClassDefinition,
                                index: Int): String {
      if (classDefinition.name.endsWith(';'))
        return "${classDefinition.name.removeSuffix(";")}!$index;"
      else
        return "${classDefinition.name}!$index"
    }

    val clashedClassNames = mutableSetOf<String>()
    classes.forEachValue { classDefinition ->
      val className = classDefinition.name
      var clashed = false
      if (clashedClassNames.contains(className)) {
        clashed = true
      }
      else {
        val clashedClass = stringToClassDefinition.remove(className)
        // If there is more than one class with this name, rename first class too.
        if (clashedClass != null) {
          clashed = true
          val newDefinition = clashedClass.copyWithName(getClashedNameWithIndex(clashedClass, 1))
          stringToClassDefinition[newDefinition.name] = newDefinition
          classes.put(clashedClass.id, newDefinition)
          clashedClassNames.add(className)
        }
      }
      if (clashed) {
        var i = 2
        var newName: String
        do {
          newName = getClashedNameWithIndex(classDefinition, i)
          i++
        }
        while (stringToClassDefinition.containsKey(newName))

        val newClassDefinition = classDefinition.copyWithName(newName)
        stringToClassDefinition[newName] = newClassDefinition
        classes.put(classDefinition.id, newClassDefinition)
      }
      else {
        stringToClassDefinition[classDefinition.name] = classDefinition
      }
      true
    }
    clashedClassNames.forEach { assert(!stringToClassDefinition.contains(it)) }

    // Every heap dump should have definitions of Soft/WeakReference and java.lang.Class
    softReferenceClass = stringToClassDefinition["java.lang.ref.SoftReference"]!!
    weakReferenceClass = stringToClassDefinition["java.lang.ref.WeakReference"]!!
    classClass = stringToClassDefinition["java.lang.Class"]!!

    Type.values().forEach { type ->
      if (type == Type.OBJECT) {
        return@forEach
      }
      stringToClassDefinition[type.getClassNameOfPrimitiveArray()]?.let { classDefinition ->
        primitiveArrayToClassDefinition.put(type, classDefinition)
      }
    }

    val shortNameToClassDefinition = HashMap<String, ClassDefinition?>()
    classes.forEachValue {
      if (it.name.contains('$')) return@forEachValue true
      val prettyName = it.prettyName
      val shortPrettyName = prettyName.substringAfterLast('.')
      if (shortNameToClassDefinition.containsKey(shortPrettyName)) {
        val prevClassDefinition = shortNameToClassDefinition[shortPrettyName]
        if (prevClassDefinition != null) {
          shortNameToClassDefinition[shortPrettyName] = null
        }
      }
      else {
        shortNameToClassDefinition[shortPrettyName] = it
      }
      true
    }
    shortNameToClassDefinition.forEach { (_, classDef) ->
      if (classDef == null) return@forEach
      classDefinitionToShortPrettyName.add(classDef)
    }
  }

  operator fun get(id: Int): ClassDefinition {
    return classes[id.toLong()]!!
  }

  operator fun get(id: Long): ClassDefinition {
    return classes[id]!!
  }

  operator fun get(name: String): ClassDefinition {
    return stringToClassDefinition[name]!!
  }

  fun getClassIfExists(name: String): ClassDefinition? {
    return stringToClassDefinition[name]
  }

  fun containsClass(name: String) = stringToClassDefinition.containsKey(name)

  fun getClassForPrimitiveArray(t: Type): ClassDefinition? {
    return primitiveArrayToClassDefinition[t]
  }

  fun size() = classes.size()

  fun isSoftOrWeakReferenceClass(classDefinition: ClassDefinition): Boolean {
    return classDefinition == softReferenceClass || classDefinition == weakReferenceClass
  }

  fun forEachClass(func: (ClassDefinition) -> Unit) {
    classes.forEachValue {
      func(it)
      true
    }
  }

  fun createStoreWithRemappedIDs(remappingFunction: LongUnaryOperator): ClassStore {
    fun map(id: Long): Long = remappingFunction.applyAsLong(id)
    val newClasses = TLongObjectHashMap<ClassDefinition>()
    classes.forEachValue {
      newClasses.put(map(it.id), it.copyWithRemappedIDs(remappingFunction))
      true
    }
    return ClassStore(newClasses)
  }

  fun getShortPrettyNameForClass(classDefinition: ClassDefinition): String {
    if (classDefinition.name.contains('$')) {
      val outerClass = stringToClassDefinition[classDefinition.name.substringBefore('$')]
      if (outerClass != null) {
        if (classDefinitionToShortPrettyName.contains(outerClass)) {
          return classDefinition.prettyName.substringAfterLast('.')
        }
      }
    }
    else if (classDefinitionToShortPrettyName.contains(classDefinition)) {
      return classDefinition.prettyName.substringAfterLast('.')
    }
    return classDefinition.prettyName
  }
}
