internal class DebugValueAdapter(
  valueName: String,
  val icon: Integer?,
) : DebugValue(valueName) {

    constructor(adapter: DebugValueAdapter, newName: String) : this("", adapter.icon) {
      myName = newName
    }

}