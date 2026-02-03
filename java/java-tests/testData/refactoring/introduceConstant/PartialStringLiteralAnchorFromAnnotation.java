@Model(usages = @Usage("${<selection>__REQUEST_PARAMETERS</selection>}"))
public class SomeClass {

  public String getModelName() {
    return "__REQUEST_PARAMETERS";
  }
}

@interface Model {
  Usage usages();
}
@interface Usage {
  String value();
}
