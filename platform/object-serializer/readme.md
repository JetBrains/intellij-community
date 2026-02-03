Default constructor is not required for objects, but highly recommended.

[Objenesis](https://github.com/easymock/objenesis) doesn't call class constructor at all, it means that field initializers will be not called and you can get strange NPEs on runtime. So, some constructor is always required.

Create default constructor and initialize final fields with some values. `beanConstructed` event can be used to resolve or check object.
Or if it is not an option, annotate constructor with `PropertyMapping`.

Annotation `PropertyMapping` impacts deserialization performance and still leave question opened — what if not all required fields are defined? Still, annotation was supported as solution because quite often better to not change classes just because of serialization needs. 

Note about parameters names — you have to specify names because option to store formal parameter names [turned off](https://stackoverflow.com/a/32348437/1910191) by default (and it is not wise to enable it since parameter names are required only for a few constructors).