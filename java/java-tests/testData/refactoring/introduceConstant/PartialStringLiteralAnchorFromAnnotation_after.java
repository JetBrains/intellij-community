@Model(usages = @Usage("${" + SomeClass.xxx + "}"))
public class SomeClass {

    public static final String xxx = "__REQUEST_PARAMETERS";

    public String getModelName() {
    return xxx;
  }
}

@interface Model {
  Usage usages();
}
@interface Usage {
  String value();
}
