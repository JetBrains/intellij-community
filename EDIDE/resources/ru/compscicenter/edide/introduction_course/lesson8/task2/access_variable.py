class MyClass:
    variable1 = 1
    variable2 = 2

    def foo(self):     # we'll explain self parameter later in task 4
        print("Hello from function foo")

my_object = MyClass()
my_object1 = MyClass()

my_object.variable2 = 3     # change value stored in variable2 in my_object

print(my_object.variable2)
print(my_object1.variable2)

my_object.foo()   # call method foo() of object my_object

print(value of variable1 from my_object)

