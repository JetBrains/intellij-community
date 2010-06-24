interface MyCloneable {

    //protected method from java.lang.Object is not implicitly declared in interface with no base interfaces
    <error descr="'clone()' in 'MyCloneable' clashes with 'clone()' in 'java.lang.Object'; attempting to use incompatible return type">int</error> clone();

    <error descr="'toString()' in 'MyCloneable' clashes with 'toString()' in 'java.lang.Object'; attempting to use incompatible return type">int</error> toString();
}