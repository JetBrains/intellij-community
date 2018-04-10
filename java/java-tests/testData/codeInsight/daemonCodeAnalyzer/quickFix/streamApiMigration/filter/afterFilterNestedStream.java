// "Replace with findFirst()" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  class MappedField{
    List<MappedField> getLoadNames(){return null;}
  }

  public MappedField getMappedField(final String storedName) {
    List<MappedField> persistenceFields = new ArrayList<>();
      return persistenceFields.stream().filter(mf -> mf.getLoadNames().stream().anyMatch(storedName::equals)).findFirst().orElse(null);
  }
}