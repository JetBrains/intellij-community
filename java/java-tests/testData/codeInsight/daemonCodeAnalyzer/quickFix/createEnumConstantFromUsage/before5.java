// "Create Enum Constant 'Bean3'" "true"
@interface BeanAware {
    BeanName[] value();
}

enum BeanName {
    Bean1,
    Bean2;
}

@BeanAware(value = BeanName.Bean3<caret>)
interface MyBeanAware {
}