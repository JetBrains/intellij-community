
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

class X {
  public static Map<String, String> dumpCurrentRequestHttpHeaders(HttpServletRequest req) {
    if (req == null) {
      return null;
    }

    return req.getHeaderNames().stream().collect(toMap((String headerName) -> headerName,
                                                       (String headerName) -> req.getHeaders(headerName).stream().collect(joining(", ")), (String s1, String s2) -> s1 + ", " + s2,
                                                       TreeMap::new));
  }

  interface HttpServletRequest {
    List<String> getHeaderNames();
    List<String> getHeaders(String header);
  }
}