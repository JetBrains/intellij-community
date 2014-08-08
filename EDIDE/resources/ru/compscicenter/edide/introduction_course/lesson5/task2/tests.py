from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''name = "John"
age = 17

print(name == "John" or age == 17)

print("name" is "Ellis" or not ("name" equal "John" and he is 17 years old))''',
                     '''name = "John"
age = 17

print(name == "John" or age == 17)

print()''', "Use 'and', 'or' and 'not' keywords")
