from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''name = "John"
age = 17

print(name == "John" or age == 17)

print(John is not 23 years old)''',
                     '''name = "John"
age = 17

print(name == "John" or age == 17)

print()''', "Use and keyword and != operator")
