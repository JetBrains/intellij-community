package pkg;

import java.lang.annotation.*;

public class ExtendsObjectAnnotated extends @TA Object {}

@Target(ElementType.TYPE_USE)
@interface TA {}