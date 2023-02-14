import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.function.Predicate;

public interface MismatchedJavadocCode {
  /**
   * @return <warning descr="Method is specified to return 'true' but its return type is not boolean">true</warning> if data is found; <warning descr="Method is specified to return 'false' but its return type is not boolean">false</warning> otherwise
   */
  int getData();

  /**
   * @return <warning descr="Method is specified to return 'true' but its return type is not boolean">true</warning> if data is found;
   * <warning descr="Method is specified to return 'false' but its return type is not boolean">false</warning> otherwise
   */
  int getData1();

  /**
   * Returns <warning descr="Method is specified to return 'true' but its return type is not boolean">true</warning> if data is found; false otherwise
   */
  int getData2();

  /**
   * @return true if data is found; false otherwise
   */
  boolean getDataOk();

  /**
   * @return true if data is found; false otherwise
   */
  Boolean getDataOk2();

  /**
   * @return an optional containing true if data is found; false otherwise
   */
  Optional<Boolean> getDataOk3();

  /**
   * @return a predicate returning true if data is found; false otherwise
   */
  Predicate<String> getDataOk4();

  /**
   * @return name of the user; <warning descr="Method is specified to return 'null' but its return type is primitive">null</warning> if not found
   */
  boolean hasUser();

  /**
   * @return name of the user; <warning descr="Method is specified to return 'null' but it's annotated as @NotNull">null</warning> if not found
   */
  @NotNull String getUserName();

  /**
   * @return name of the user; null if not found
   */
  String getUserNameOk();

  /**
   * @return value; <warning descr="Method is specified to return 'NULL' but there's no such enum constant in Value">NULL</warning> if not specified
   */
  Value getValue();

  /**
   * @return <warning descr="Method is specified to return list but the return type is array">list</warning> of names
   */
  String[] getNames();

  /**
   * @return array of names
   */
  String[] getNamesOk();

  /**
   * @return <warning descr="Method is specified to return set but the return type is list">set</warning> of names
   */
  List<String> getNameList();

  /**
   * @return a list of names
   */
  List<String> getNameListOk();

  /**
   * @return the <warning descr="Method is specified to return array but the return type is set">array</warning> of names
   */
  Set<String> getNameSet();

  /**
   * @return set of names
   */
  Set<String> getNameSetOk();

  /**
   * @return a <warning descr="Method is specified to return string but the return type is number">string</warning> describing the value
   */
  int getIntValue();

  /**
   * @return a number describing the value
   */
  Integer getIntValueOk();

  /**
   * @return <warning descr="Method is specified to return number but the return type is boolean">number</warning> that corresponds to something
   */
  boolean getFlag();

  /**
   * @return a <warning descr="Method is specified to return number but the return type is array">number</warning> of items in the data
   */
  int[] getIntArray();

  /**
   * @return a list of comma-separated values
   */
  String getDataString();

  /**
   * @return a <warning descr="Method is specified to return string but the return type is list">string</warning> containing something
   */
  List<String> getDataStringList();

  /**
   * @return <warning descr="Method is specified to return count but the return type is list">count</warning> of users
   */
  List<String> getUserNames();

  /**
   * @return a <warning descr="Method is specified to return a single StringBuilder but the return type is a collection">StringBuilder</warning> containing the data
   */
  List<StringBuilder> getStringBuilder();

  enum Value {
    NONE, ONE, TWO
  }
}
