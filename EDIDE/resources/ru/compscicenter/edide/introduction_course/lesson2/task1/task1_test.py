from test_helper import run_common_tests


if __name__ == '__main__':
    run_common_tests('''a = b = 2
print("a = " + str(a))
print("b = " + str(b))

greetings = "greetings"
print("greetings = " + str(greetings))
greetings = another value
print("greetings = " + str(greetings))''', '''a = b = 2
print("a = " + str(a))
print("b = " + str(b))

greetings = "greetings"
print("greetings = " + str(greetings))
greetings =
print("greetings = " + str(greetings))''', "You should redefine variable 'greetings'")

# TODO: help with non-ASCII characters