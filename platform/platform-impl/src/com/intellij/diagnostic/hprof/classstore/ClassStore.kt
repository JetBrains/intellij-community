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

  private val softReferenceClass: ClassDefinition
  private val weakReferenceClass: ClassDefinition
  val classClass: ClassDefinition

  private val primitiveArrayToClassDefinition = HashMap<Type, ClassDefinition>()

  init {
    classes.forEachValue { classDefinition ->
      if (stringToClassDefinition.containsKey(classDefinition.name)) {
        var i = 1
        var newName: String
        do {
          i++
          newName = classDefinition.name + "($i)"
        }
        while (stringToClassDefinition.containsKey(newName))
        stringToClassDefinition[newName] = classDefinition.copyWithName(newName)
      }
      else {
        stringToClassDefinition[classDefinition.name] = classDefinition
      }
      true
    }

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
    shortNameToClassDefinition.forEach { _, classDef ->
      if (classDef == null) return@forEach
      classDefinitionToShortPrettyName.add(classDef)
    }
  }

  companion object {
    fun create(parser: HProfEventBasedParser): ClassStore {
      val pass1 = CreateClassStoreVisitor()
      parser.accept(pass1, "getClassDefinitions-pass1: create class objects")
      val pass2 = CollectStringValuesVisitor(pass1.getStringIDToStringMap())
      parser.accept(pass2, "getClassDefinitions-pass2: get strings")
      pass1.updateNames()
      return pass1.getClassStore()
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
