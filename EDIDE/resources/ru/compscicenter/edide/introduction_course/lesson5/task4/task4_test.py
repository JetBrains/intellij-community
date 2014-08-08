from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''x = 28

if x < 0:
    print('x < 0')
elif x == 0:
    print('x is zero')
elif x == 1:
    print('x == 1')
else:
    print('non of the above is true')

name = "John"

print True if name equal to "John" and False otherwise''',
                     '''x = 28

if x < 0:
    print('x < 0')
elif x == 0:
    print('x is zero')
elif x == 1:
    print('x == 1')
else:
    print('non of the above is true')

name = "John"''', "Use if/else keywords")
