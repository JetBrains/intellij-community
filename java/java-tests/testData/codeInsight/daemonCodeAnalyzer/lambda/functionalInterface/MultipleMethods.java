interface Bar { boolean equals(Object obj); }

@FunctionalInterface
interface Foo extends Bar { int compare(String o1, String o2); }
