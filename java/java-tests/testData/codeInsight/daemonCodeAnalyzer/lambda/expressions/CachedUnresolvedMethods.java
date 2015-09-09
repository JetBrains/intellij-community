import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collector;

class Test {

  void f() {
    JSONObject deviceTokenJson = new ArrayList<DeviceToken>()
      .stream()
      .collect(JSON.toObject(
        token -> Hex.encodeHexString(token.get<caret>Token()) ,
        token -> new JSONObject()
          .put("application_id", token.getAppId())
          .put("sandbox", token.isSandbox())));

  }
  
  static class JSONObject {
    public JSONObject put(String var1, Object var2) {
      return this;
    }
  }

  static class JSON {
    static <T, R> Collector<T, ?, R> toObject(Function<DeviceToken, String> f, Function<T, R> ff) {
      return null;
    }
  }

  static private class Hex {
    static String encodeHexString(byte[] t) {
      return new String(t);
    }

  }

  private static class DeviceToken {
    public byte[] getToken() {
      return null;
    }
    public String getAppId() {
      return "";
    }

    public boolean isSandbox() {
      return true;
    }
  }
}