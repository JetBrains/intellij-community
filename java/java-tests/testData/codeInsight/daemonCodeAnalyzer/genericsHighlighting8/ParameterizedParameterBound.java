import java.util.*;
class ParamHolder<T extends List<String>> {}
class HolderUsage {
        public void fail(ParamHolder<<error descr="Type parameter 'java.util.List' is not within its bound; should extend 'java.util.List<java.lang.String>'">List</error>> p) { }
        public void success(ParamHolder<ArrayList<String>> p) { }
}
