from test_helper import run_common_tests


if __name__ == '__main__':
    run_common_tests('''name = "John"
print("Hello, PyCharm! My name is %s!" % name)

print("I'm special symbol years old" % years)''', '''name = "John"
print("Hello, PyCharm! My name is %s!" % name)

print("I'm  years old" % )''', "You should modify the file")

    import sys
    path = sys.argv[-1]
# TODO: check it's not hardcoded