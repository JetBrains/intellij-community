
interface OraModMajorObject extends OraMajorObject {}
interface OraModStoredSchemaObject extends OraStoredSchemaObject {}
interface OraModCluster extends OraCluster, OraModMajorObject, OraModStoredSchemaObject {}

interface OraStoredSchemaObject {
  default boolean isNameSurrogate() {
    return false;
  }
}
interface OraMajorObject  {
  default boolean isNameSurrogate() {
    return false;
  }
}
interface OraCluster extends OraMajorObject, OraStoredSchemaObject {
  default boolean isNameSurrogate() {
    return false;
  }
}