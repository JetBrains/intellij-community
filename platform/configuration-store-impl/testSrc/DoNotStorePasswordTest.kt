
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.testFramework.ProjectRule
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jdom.Attribute
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Test
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.function.BiPredicate

class DoNotStorePasswordTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun printPasswordComponents() {
    val processor = BiPredicate<Class<*>, PluginDescriptor?> { aClass, _ ->
      val stateAnnotation = StoreUtil.getStateSpec(aClass)
      if (stateAnnotation == null || stateAnnotation.name.isEmpty()) {
        return@BiPredicate true
      }

      for (i in aClass.genericInterfaces) {
        if (checkType(i)) {
          return@BiPredicate true
        }
      }


      // public static class Project extends WebServersConfigManagerBaseImpl<WebServersConfigManagerBaseImpl.State> {
      // so, we check not only PersistentStateComponent
      checkType(aClass.genericSuperclass)

      true
    }

    val app = ApplicationManager.getApplication() as ApplicationImpl
    ServiceManagerImpl.processAllImplementationClasses(app, processor)
    // yes, we don't use default project here to be sure
    ServiceManagerImpl.processAllImplementationClasses(projectRule.project as ComponentManagerImpl, processor)

    @Suppress("DEPRECATION")
    for (c in app.getComponentInstancesOfType(PersistentStateComponent::class.java)) {
      processor.test(c.javaClass, null)
    }
    @Suppress("DEPRECATION")
    for (c in (projectRule.project as ComponentManagerImpl).getComponentInstancesOfType(PersistentStateComponent::class.java)) {
      processor.test(c.javaClass, null)
    }
  }

  private fun isSavePasswordField(name: String) = name.contains("remember", ignoreCase = true) || name.contains("keep", ignoreCase = true) || name.contains("save", ignoreCase = true)

  fun check(clazz: Class<*>) {
    if (clazz === Attribute::class.java || clazz === Element::class.java) {
      return
    }

    for (accessor in XmlSerializerUtil.getAccessors(clazz)) {
      val name = accessor.name
      if (name.contains("password", ignoreCase = true) && !isSavePasswordField(name)) {
        System.out.println("${clazz.typeName}.${accessor.name}")
      }
      else if (!accessor.valueClass.isPrimitive) {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        if (Collection::class.java.isAssignableFrom(accessor.valueClass)) {
          val genericType = accessor.genericType
          if (genericType is ParameterizedType) {
            val type = genericType.actualTypeArguments[0]
            if (type is Class<*>) {
              check(type)
            }
          }
        }
        else if (accessor.valueClass != clazz) {
          check(accessor.valueClass)
        }
      }
    }
  }

  private fun checkType(type: Type): Boolean {
    if (type !is ParameterizedType || !PersistentStateComponent::class.java.isAssignableFrom(type.rawType as Class<*>)) {
      return false
    }

    type.actualTypeArguments[0].let {
      if (it is ParameterizedType) {
        check(it.rawType as Class<*>)
      }
      else {
        check(it as Class<*>)
      }
    }
    return true
  }
}