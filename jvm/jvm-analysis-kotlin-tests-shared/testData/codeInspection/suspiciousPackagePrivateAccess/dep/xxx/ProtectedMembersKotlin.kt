package xxx

open class ProtectedMembersKotlin {
  protected val property: String = ""

  protected fun foo() {}

  protected val String.extensionProperty: String
    get() = ""

  protected fun String.extensionFunction(): String = ""
}