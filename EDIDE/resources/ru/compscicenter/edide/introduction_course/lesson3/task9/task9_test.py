from test_helper import run_common_tests


if __name__ == '__main__':
    run_common_tests('''monty_python = "Monty Python"
print(monty_python)

print(monty_python.lower())

print(upper cased monty_python)''', '''monty_python = "Monty Python"
print(monty_python)

print(monty_python.lower())

print()''', "You should modify the file")

    import sys
    path = sys.argv[-1]
# TODO: check it's not hardcoded