class Test {
  {
    class TypedQuery<T> {}
    class TemporalDataDTO<K> {}
    class FOLDER_ID {}

    TypedQuery<TemporalDataDTO> h = null;
    TypedQuery<TemporalDataDTO<FOLDER_ID>> typedQuery = (TypedQuery<TemporalDataDTO<FOLDER_ID>>) (TypedQuery<?>)h;
  }
}