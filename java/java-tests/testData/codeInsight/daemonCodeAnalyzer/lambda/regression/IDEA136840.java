import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toMap;

class Test {
  public CompletableFuture<Map<String, VolPercentile>> getPercentile(List<String> symbols) {
    List<NameValuePair> params = new ArrayList<>(5);
    return getPercentile(params, symbols)
      .thenApplyAsync((map) -> map.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> new VolPercentile(e.getValue()))));
  }

  public CompletableFuture<Map<String, Double>> getPercentile(List<NameValuePair> params, List<String> symbols) {
    return null;
  }
}

class NameValuePair {}
class VolPercentile implements Serializable {
  public VolPercentile(Double amount) {}
}
