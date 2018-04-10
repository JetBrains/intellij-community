// "Replace Optional.isPresent() condition with functional style expression" "true"
import java.util.Optional;

class Trip implements Comparable<Trip> {
  Optional<Integer> originId;

  public Trip(Optional<Integer> originId) {
    this.originId = originId;
  }

  public Optional<Integer> getOriginId() {
    return originId;
  }

  @Override
  public int compareTo(Trip o) {
    return this.originId.isPresent() ?
           (o.originId.map(integer -> Integer.compare(this.originId.get(), integer)).orElse(-1)) :
           (o.originId.isPresent() ? 1 : 0);
  }
}