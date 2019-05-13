class X {
  public class ObjectValue {
    public ObjectValue get(String name) { return null; }
  }

  public class JsonObjectValue extends ObjectValue {
    public int sizeInBytes() { return 0; }
  }

  {
    JsonObjectValue obj = null;
    obj.get("href").siz<caret>eInBytes();
  }

}