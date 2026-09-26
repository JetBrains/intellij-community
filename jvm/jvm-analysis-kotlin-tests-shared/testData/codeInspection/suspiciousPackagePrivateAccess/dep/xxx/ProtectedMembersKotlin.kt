package xxx

open class ProtectedMembersKotlin {
  protected val property: String = ""

  protected fun foo() {}

  protected val String.extensionProperty: String
    get() = ""

  protected fun String.extensionFunction(): String = ""

  companion object {
    @JvmStatic
    protected val jvmStaticProperty: String = ""

    @JvmStatic
    protected fun jvmStaticFunction(): Unit = Unit
  }
}