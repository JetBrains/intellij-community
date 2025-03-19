@file:Suppress("SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.storage

import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import org.jetbrains.jps.dependency.FactoredExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.java.AnnotationUsage
import org.jetbrains.jps.dependency.java.ClassAsGenericBoundUsage
import org.jetbrains.jps.dependency.java.ClassNewUsage
import org.jetbrains.jps.dependency.java.ClassPermitsUsage
import org.jetbrains.jps.dependency.java.ClassUsage
import org.jetbrains.jps.dependency.java.FieldAssignUsage
import org.jetbrains.jps.dependency.java.FieldUsage
import org.jetbrains.jps.dependency.java.FileNode
import org.jetbrains.jps.dependency.java.ImportPackageOnDemandUsage
import org.jetbrains.jps.dependency.java.ImportStaticMemberUsage
import org.jetbrains.jps.dependency.java.ImportStaticOnDemandUsage
import org.jetbrains.jps.dependency.java.JvmClass
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.java.KotlinMeta
import org.jetbrains.jps.dependency.java.LookupNameUsage
import org.jetbrains.jps.dependency.java.MethodUsage
import org.jetbrains.jps.dependency.java.ModuleUsage
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

private val lookup = MethodHandles.lookup()

internal data class ClassInfo(
  @JvmField val aClass: Class<*>,
  @JvmField val id: Int,
  @JvmField val constructor: MethodHandle,
  @JvmField val isFactored: Boolean,
)

internal object ClassRegistry {
  private val classToInfo: ScatterMap<Class<*>, ClassInfo>
  private val idToInfo: IntObjectMap<ClassInfo>

  init {
    val defaultReadConstructorType = MethodType.methodType(Void.TYPE, GraphDataInput::class.java)
    val factorMethodType = MethodType.methodType(Void.TYPE, JvmNodeReferenceID::class.java, GraphDataInput::class.java)

    val classToInfo = MutableScatterMap<Class<*>, ClassInfo>(17)
    val idToInfo = MutableIntObjectMap<ClassInfo>(17)

    var counter = 0
    fun registerClass(aClass: Class<*>) {
      val id = counter++
      val info = if (FactoredExternalizableGraphElement::class.java.isAssignableFrom(aClass)) {
        ClassInfo(aClass = aClass, id = id, constructor = lookup.findConstructor(aClass, factorMethodType), isFactored = true)
      }
      else {
        ClassInfo(aClass = aClass, id = id, constructor = lookup.findConstructor(aClass, defaultReadConstructorType), isFactored = false)
      }
      classToInfo.put(aClass, info)
      idToInfo.put(info.id, info)
    }

    registerClass(JvmClass::class.java)
    registerClass(FileNode::class.java)
    registerClass(JvmNodeReferenceID::class.java)
    registerClass(KotlinMeta::class.java)

    registerClass(AnnotationUsage::class.java)

    registerClass(ClassUsage::class.java)
    registerClass(ClassNewUsage::class.java)
    registerClass(ClassAsGenericBoundUsage::class.java)
    registerClass(ClassPermitsUsage::class.java)
    registerClass(ModuleUsage::class.java)

    registerClass(LookupNameUsage::class.java)
    registerClass(MethodUsage::class.java)
    registerClass(FieldUsage::class.java)
    registerClass(FieldAssignUsage::class.java)

    registerClass(ImportStaticMemberUsage::class.java)
    registerClass(ImportStaticOnDemandUsage::class.java)
    registerClass(ImportPackageOnDemandUsage::class.java)

    classToInfo.trim()
    idToInfo.trim()

    this.classToInfo = classToInfo
    this.idToInfo = idToInfo

    require(counter <= 255)
  }

  fun writeClassId(aClass: Class<*>, out: GraphDataOutput): ClassInfo {
    val info = classToInfo.get(aClass)
    if (info == null) {
      throw IllegalStateException("Class $aClass is not registered")
    }
    out.writeByte(info.id)
    return info
  }

  fun read(input: GraphDataInput): ClassInfo {
    val id = input.readUnsignedByte()
    return idToInfo.get(id) ?: throw IllegalStateException("Class with id $id is not registered")
  }
}