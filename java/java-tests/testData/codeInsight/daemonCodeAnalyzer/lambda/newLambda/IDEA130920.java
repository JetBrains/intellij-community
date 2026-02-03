import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class FieldCommand implements Comparable<FieldCommand> {
  protected DataFieldMeta getMeta() {
    return null;
  }
}

abstract class DataFieldMeta implements Comparable<DataFieldMeta> {
  protected String getId() {
    return "";
  }
}

interface DataFieldRepository {
  List<ValueHolder> getStrings(String dataFieldPath, Object subject, Core1Account account);
}

final class ValueHolder {
  DataFieldMeta getMetadata() {
    return null;
  }
}

interface Core1Account{}

class Test {
  public Map<String, ValueHolder> foo(final DataFieldRepository repository, final List<FieldCommand> fields, final Object sourceObject, final Core1Account account) {
    return fields
      .stream()
      .map(field -> field.getMeta().getId())
      .distinct()
      .map(dataFieldId -> repository.getStrings(dataFieldId, sourceObject, account))
      .filter(values -> !values.isEmpty())
      .map(values -> values.get(0))
      .collect(Collectors.toMap(
        value -> value.getMetadata().getId(),
        value -> value
      ));
  }
}