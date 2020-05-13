class BasicLazyResolveTest {


    public void test() {

        Object session = <selection>new Object() {

/*
            public ClassMemberDeclarationProvider getClassMemberDeclarationProvider(JetClass jetClassOrObject) {
                final JetClass jetClass = (JetClass) jetClassOrObject;
                return new ClassMemberDeclarationProvider() {*/


                    /*     private <D, T extends JetNamed> List<T> filter(List<D> list, final Class<T> t, final Name name) {
                        //noinspection unchecked
                        return (List) Lists.newArrayList(Collections2.filter(list, new Predicate<D>() {
                            @Override
                            public boolean apply(D d) {
                                return t.isInstance(d) && ((JetNamed) d).getNameAsName().equals(name);
                            }
                        }));
                    }*/


             /*   };
            }*/
        }</selection>;

        String packageDescriptor = session.toString();

    }


}
